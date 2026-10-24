package com.navroopbath.jobtracker.auth;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.navroopbath.jobtracker.TestcontainersConfiguration;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class LoginTests {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private RestTestClient client;

    @Test
    void correctPasswordReturnsABearerToken() {
        String email = registerNewUser();

        login(email, PASSWORD)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").exists()
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.expiresIn").isEqualTo(3600);
    }

    @Test
    void tokenGivesAccessToProtectedEndpoints() {
        String email = registerNewUser();
        String token = loginAndGetToken(email);

        client.get().uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo(email);
    }

    @Test
    void wrongPasswordIsRejected() {
        String email = registerNewUser();

        login(email, "not the right password at all")
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Invalid email or password.");
    }

    @Test
    void unknownEmailGetsTheSameErrorAsAWrongPassword() {
        login(uniqueEmail(), PASSWORD)
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Invalid email or password.");
    }

    @Test
    void protectedEndpointWithoutATokenIsRejected() {
        client.get().uri("/api/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = loginAndGetToken(registerNewUser());

        client.get().uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperWithSignature(token))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private String registerNewUser() {
        String email = uniqueEmail();
        client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", PASSWORD))
                .exchange()
                .expectStatus().isCreated();
        return email;
    }

    private RestTestClient.ResponseSpec login(String email, String password) {
        return client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .exchange();
    }

    private String loginAndGetToken(String email) {
        TokenResponse response = login(email, PASSWORD)
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody();
        return response.accessToken();
    }

    private static String tamperWithSignature(String token) {
        char[] chars = token.toCharArray();
        int position = token.lastIndexOf('.') + 5;
        chars[position] = chars[position] == 'A' ? 'B' : 'A';
        return new String(chars);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
