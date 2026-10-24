package com.navroopbath.jobtracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.navroopbath.jobtracker.TestcontainersConfiguration;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class RegistrationTests {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void registeringCreatesAnAccountWithALowercaseEmail() {
        String email = uniqueEmail();

        register(email.toUpperCase(Locale.ROOT), PASSWORD)
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").exists()
                .jsonPath("$.email").isEqualTo(email)
                .jsonPath("$.password").doesNotExist()
                .jsonPath("$.passwordHash").doesNotExist();
    }

    @Test
    void passwordIsStoredAsAnArgon2idHash() {
        String email = uniqueEmail();
        register(email, PASSWORD).expectStatus().isCreated();

        String storedHash = jdbc.sql("SELECT password_hash FROM users WHERE email = ?")
                .param(email)
                .query(String.class)
                .single();

        assertThat(storedHash)
                .startsWith("{argon2}$argon2id$")
                .contains("m=19456,t=2,p=1")
                .doesNotContain(PASSWORD);
    }

    @Test
    void duplicateEmailIsRejectedEvenWithDifferentCase() {
        String email = uniqueEmail();
        register(email, PASSWORD).expectStatus().isCreated();

        register(email.toUpperCase(Locale.ROOT), PASSWORD)
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("An account with this email already exists.");
    }

    @Test
    void shortPasswordIsRejected() {
        register(uniqueEmail(), "too-short").expectStatus().isBadRequest();
    }

    @Test
    void invalidEmailIsRejected() {
        register("not-an-email", PASSWORD).expectStatus().isBadRequest();
    }

    private RestTestClient.ResponseSpec register(String email, String password) {
        return client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .exchange();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
