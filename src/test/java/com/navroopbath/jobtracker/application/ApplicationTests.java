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
import org.springframework.test.web.servlet.client.EntityExchangeResult;
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
    void createReturnsTheNewApplicationsLocation() {
        String token = newUserToken();

        EntityExchangeResult<ApplicationResponse> result =
                create(token, Map.of("company", "Acme", "jobTitle", "Software Engineer"))
                        .expectStatus().isCreated()
                        .expectBody(ApplicationResponse.class)
                        .returnResult();
        ApplicationResponse created = result.getResponseBody();
        String location = result.getResponseHeaders().getFirst(HttpHeaders.LOCATION);

        assertThat(location).isEqualTo("/api/applications/" + created.id());
        assertThat(get(token, created.id())).isEqualTo(created);
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
    void anotherUsersApplicationLooksMissing() {
        String alice = newUserToken();
        String bob = newUserToken();
        ApplicationResponse alicesApplication = saveApplication(alice, "Acme");
        Long id = alicesApplication.id();

        fetch(bob, id).expectStatus().isNotFound()
                .expectBody().jsonPath("$.detail").isEqualTo("Application not found.");
        replace(bob, id, Map.of("company", "Changed", "jobTitle", "Changed", "status", "REJECTED"))
                .expectStatus().isNotFound();
        remove(bob, id).expectStatus().isNotFound();

        assertThat(get(alice, id)).isEqualTo(alicesApplication);
    }

    @Test
    void unknownIdIsNotFound() {
        fetch(newUserToken(), Long.MAX_VALUE).expectStatus().isNotFound()
                .expectBody().jsonPath("$.detail").isEqualTo("Application not found.");
    }

    @Test
    void putReplacesTheApplication() {
        String token = newUserToken();
        ApplicationResponse created = saveApplication(token, "Acme");

        ApplicationResponse updated = replace(token, created.id(), Map.of(
                "company", "  Acme Corp  ",
                "jobTitle", "Backend Engineer",
                "postingUrl", "https://job-boards.greenhouse.io/acme/jobs/123",
                "status", "INTERVIEW",
                "appliedOn", "2026-10-20",
                "notes", "Phone screen on Friday"))
                .expectStatus().isOk()
                .expectBody(ApplicationResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.company()).isEqualTo("Acme Corp");
        assertThat(updated.jobTitle()).isEqualTo("Backend Engineer");
        assertThat(updated.postingUrl()).isEqualTo("https://job-boards.greenhouse.io/acme/jobs/123");
        assertThat(updated.status()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(updated.appliedOn()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(updated.notes()).isEqualTo("Phone screen on Friday");
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
        assertThat(updated.updatedAt()).isAfter(created.updatedAt());

        // The response must match what was actually stored.
        assertThat(get(token, created.id())).isEqualTo(updated);
    }

    @Test
    void fieldsLeftOutOfAPutAreCleared() {
        String token = newUserToken();
        ApplicationResponse created = create(token, Map.of(
                "company", "Acme",
                "jobTitle", "Software Engineer",
                "postingUrl", "https://job-boards.greenhouse.io/acme/jobs/123",
                "status", "APPLIED",
                "appliedOn", "2026-10-20",
                "notes", "Referred by a classmate"))
                .expectStatus().isCreated()
                .expectBody(ApplicationResponse.class)
                .returnResult()
                .getResponseBody();

        replace(token, created.id(), Map.of("company", "Acme", "jobTitle", "Software Engineer", "status", "APPLIED"))
                .expectStatus().isOk();

        ApplicationResponse stored = get(token, created.id());
        assertThat(stored.postingUrl()).isNull();
        assertThat(stored.appliedOn()).isNull();
        assertThat(stored.notes()).isNull();
    }

    @Test
    void putRequiresAStatus() {
        String token = newUserToken();
        ApplicationResponse created = saveApplication(token, "Acme");

        replace(token, created.id(), Map.of("company", "Acme", "jobTitle", "Software Engineer"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(1)
                .jsonPath("$.errors[0].field").isEqualTo("status");
    }

    @Test
    void deletedApplicationIsGone() {
        String token = newUserToken();
        ApplicationResponse created = saveApplication(token, "Acme");

        remove(token, created.id()).expectStatus().isNoContent();

        fetch(token, created.id()).expectStatus().isNotFound();
        assertThat(list(token)).isEmpty();
        remove(token, created.id()).expectStatus().isNotFound();
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
    void validationErrorsNameEachInvalidField() {
        create(newUserToken(), Map.of(
                "company", "   ",
                "jobTitle", "Software Engineer",
                "postingUrl", "javascript:alert(1)"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.detail").isEqualTo("Some fields are invalid.")
                .jsonPath("$.errors.length()").isEqualTo(2)
                .jsonPath("$.errors[0].field").isEqualTo("company")
                .jsonPath("$.errors[0].message").exists()
                .jsonPath("$.errors[1].field").isEqualTo("postingUrl")
                .jsonPath("$.errors[1].message").isEqualTo("must be an http or https link");
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

        client.get().uri("/api/applications/1")
                .exchange()
                .expectStatus().isUnauthorized();

        client.put().uri("/api/applications/1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("company", "Acme", "jobTitle", "Software Engineer", "status", "SAVED"))
                .exchange()
                .expectStatus().isUnauthorized();

        client.delete().uri("/api/applications/1")
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

    private RestTestClient.ResponseSpec fetch(String token, Long id) {
        return client.get().uri("/api/applications/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();
    }

    private RestTestClient.ResponseSpec replace(String token, Long id, Map<String, String> body) {
        return client.put().uri("/api/applications/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private RestTestClient.ResponseSpec remove(String token, Long id) {
        return client.delete().uri("/api/applications/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();
    }

    private ApplicationResponse saveApplication(String token, String company) {
        return create(token, Map.of("company", company, "jobTitle", "Software Engineer"))
                .expectStatus().isCreated()
                .expectBody(ApplicationResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ApplicationResponse get(String token, Long id) {
        return fetch(token, id)
                .expectStatus().isOk()
                .expectBody(ApplicationResponse.class)
                .returnResult()
                .getResponseBody();
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
