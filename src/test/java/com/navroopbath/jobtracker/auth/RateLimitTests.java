package com.navroopbath.jobtracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.navroopbath.jobtracker.TestcontainersConfiguration;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "auth.rate-limit.login-per-ip.attempts=3",
        "auth.rate-limit.login-per-ip.period=1m",
        "auth.rate-limit.login-per-email.attempts=2",
        "auth.rate-limit.login-per-email.period=15m",
        "auth.rate-limit.registration-per-ip.attempts=2",
        "auth.rate-limit.registration-per-ip.period=1h"
})
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class RateLimitTests {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String WRONG_PASSWORD = "not the right password at all";

    // Every test uses its own client addresses, so the tests never share a limit.
    private static final AtomicInteger nextAddress = new AtomicInteger(1);

    @Autowired
    private RestTestClient client;

    @Test
    void tooManyLoginAttemptsFromOneAddressAreRejected() {
        String address = newAddress();
        for (int i = 0; i < 3; i++) {
            login(uniqueEmail(), PASSWORD, address).expectStatus().isUnauthorized();
        }

        login(uniqueEmail(), PASSWORD, address)
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().value(HttpHeaders.RETRY_AFTER,
                        seconds -> assertThat(Long.parseLong(seconds)).isBetween(1L, 20L))
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Too many login attempts. Try again later.");
    }

    @Test
    void otherAddressesAreNotAffected() {
        String busyAddress = newAddress();
        for (int i = 0; i < 3; i++) {
            login(uniqueEmail(), PASSWORD, busyAddress).expectStatus().isUnauthorized();
        }
        login(uniqueEmail(), PASSWORD, busyAddress).expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        login(uniqueEmail(), PASSWORD, newAddress()).expectStatus().isUnauthorized();
    }

    @Test
    void successfulLoginsStillCountAgainstTheAddress() {
        String email = registerNewUser();
        String address = newAddress();
        for (int i = 0; i < 3; i++) {
            login(email, PASSWORD, address).expectStatus().isOk();
        }

        login(email, PASSWORD, address).expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void repeatedFailuresForOneEmailAreRejectedFromAnyAddress() {
        String email = registerNewUser();

        login(email, WRONG_PASSWORD, newAddress()).expectStatus().isUnauthorized();
        login(email.toUpperCase(Locale.ROOT), WRONG_PASSWORD, newAddress()).expectStatus().isUnauthorized();

        // Even the right password is refused until the email's limit refills.
        login(email, PASSWORD, newAddress())
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().exists(HttpHeaders.RETRY_AFTER);
    }

    @Test
    void successfulLoginsDoNotCountAgainstTheEmail() {
        String email = registerNewUser();
        for (int i = 0; i < 4; i++) {
            login(email, PASSWORD, newAddress()).expectStatus().isOk();
        }
    }

    @Test
    void unknownEmailsAreLimitedLikeRealOnes() {
        String email = uniqueEmail();

        login(email, PASSWORD, newAddress()).expectStatus().isUnauthorized();
        login(email, PASSWORD, newAddress()).expectStatus().isUnauthorized();
        login(email, PASSWORD, newAddress()).expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void tooManyRegistrationsFromOneAddressAreRejected() {
        String address = newAddress();
        register(uniqueEmail(), address).expectStatus().isCreated();
        register(uniqueEmail(), address).expectStatus().isCreated();

        register(uniqueEmail(), address)
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().value(HttpHeaders.RETRY_AFTER,
                        seconds -> assertThat(Long.parseLong(seconds)).isBetween(1L, 1800L))
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Too many registration attempts. Try again later.");

        register(uniqueEmail(), newAddress()).expectStatus().isCreated();
    }

    @Test
    void registrationAndLoginHaveSeparateLimits() {
        String address = newAddress();
        register(uniqueEmail(), address).expectStatus().isCreated();
        register(uniqueEmail(), address).expectStatus().isCreated();
        register(uniqueEmail(), address).expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        login(uniqueEmail(), PASSWORD, address).expectStatus().isUnauthorized();
    }

    private RestTestClient.ResponseSpec login(String email, String password, String clientAddress) {
        return client.post().uri("/api/auth/login")
                .header("X-Forwarded-For", clientAddress)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .exchange();
    }

    private RestTestClient.ResponseSpec register(String email, String clientAddress) {
        return client.post().uri("/api/auth/register")
                .header("X-Forwarded-For", clientAddress)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", PASSWORD))
                .exchange();
    }

    private String registerNewUser() {
        String email = uniqueEmail();
        register(email, newAddress()).expectStatus().isCreated();
        return email;
    }

    // 198.51.100.0/24 is reserved for documentation, so these addresses never belong to anyone.
    private static String newAddress() {
        return "198.51.100." + nextAddress.getAndIncrement();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
