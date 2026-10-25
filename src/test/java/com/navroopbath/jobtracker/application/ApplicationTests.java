package com.navroopbath.jobtracker.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
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
import com.navroopbath.jobtracker.auth.TokenResponse;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ApplicationTests {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private RestTestClient client;

    @Test
    void newApplicationStartsAsSavedWithNamesTrimmed() {
        String token = newUserToken();

        create(token, Map.of("company", "  Acme  ", "jobTitle", "Software Engineer"))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").exists()
                .jsonPath("$.company").isEqualTo("Acme")
                .jsonPath("$.jobTitle").isEqualTo("Software Engineer")
                .jsonPath("$.status").isEqualTo("SAVED")
                .jsonPath("$.createdAt").exists()
                .jsonPath("$.updatedAt").exists()
                .jsonPath("$.userId").doesNotExist();
    }

    @Test
    void everyFieldIsStored() {
        String token = newUserToken();

        create(token, Map.of(
                "company", "Acme",
                "jobTitle", "Backend Engineer",
                "postingUrl", "https://job-boards.greenhouse.io/acme/jobs/123",
                "status", "APPLIED",
                "appliedOn", "2026-10-20",
                "notes", "Referred by a classmate"))
                .expectStatus().isCreated();

        List<ApplicationResponse> stored = list(token);
        assertThat(stored).hasSize(1);
        ApplicationResponse application = stored.getFirst();
        assertThat(application.postingUrl()).isEqualTo("https://job-boards.greenhouse.io/acme/jobs/123");
        assertThat(application.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(application.appliedOn()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(application.notes()).isEqualTo("Referred by a classmate");
    }

    @Test
    void eachUserSeesOnlyTheirOwnApplicationsNewestFirst() {
        String alice = newUserToken();
        String bob = newUserToken();

        saveApplication(alice, "First Co");
        saveApplication(bob, "Bob's Co");
        saveApplication(alice, "Second Co");

        assertThat(list(alice))
                .extracting(ApplicationResponse::company)
                .containsExactly("Second Co", "First Co");
        assertThat(list(bob))
                .extracting(ApplicationResponse::company)
                .containsExactly("Bob's Co");
    }

    @Test
    void missingCompanyIsRejected() {
        create(newUserToken(), Map.of("jobTitle", "Software Engineer"))
                .expectStatus().isBadRequest();
    }

    @Test
    void unknownStatusIsRejected() {
        create(newUserToken(), Map.of("company", "Acme", "jobTitle", "Software Engineer", "status", "GHOSTED"))
                .expectStatus().isBadRequest();
    }

    @Test
    void postingUrlMustBeAWebLink() {
        create(newUserToken(), Map.of(
                "company", "Acme",
                "jobTitle", "Software Engineer",
                "postingUrl", "javascript:alert(1)"))
                .expectStatus().isBadRequest();
    }

    @Test
    void applicationsRequireLogin() {
        client.get().uri("/api/applications")
                .exchange()
                .expectStatus().isUnauthorized();

        client.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("company", "Acme", "jobTitle", "Software Engineer"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private RestTestClient.ResponseSpec create(String token, Map<String, String> body) {
        return client.post().uri("/api/applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private void saveApplication(String token, String company) {
        create(token, Map.of("company", company, "jobTitle", "Software Engineer"))
                .expectStatus().isCreated();
    }

    private List<ApplicationResponse> list(String token) {
        ApplicationResponse[] applications = client.get().uri("/api/applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ApplicationResponse[].class)
                .returnResult()
                .getResponseBody();
        return List.of(applications);
    }

    private String newUserToken() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        Map<String, String> credentials = Map.of("email", email, "password", PASSWORD);

        client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(credentials)
                .exchange()
                .expectStatus().isCreated();

        TokenResponse token = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(credentials)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody();
        return token.accessToken();
    }
}
