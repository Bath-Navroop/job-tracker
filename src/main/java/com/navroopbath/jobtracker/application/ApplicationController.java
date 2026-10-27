package com.navroopbath.jobtracker.application;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
class ApplicationController {

    private final ApplicationService applicationService;

    ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    ResponseEntity<ApplicationResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateApplicationRequest request) {
        ApplicationResponse created = ApplicationResponse.from(applicationService.create(userId(jwt), request));
        return ResponseEntity.created(URI.create("/api/applications/" + created.id())).body(created);
    }

    @GetMapping
    List<ApplicationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return applicationService.listFor(userId(jwt)).stream()
                .map(ApplicationResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    ApplicationResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ApplicationResponse.from(applicationService.get(userId(jwt), id));
    }

    @PutMapping("/{id}")
    ApplicationResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody UpdateApplicationRequest request) {
        return ApplicationResponse.from(applicationService.update(userId(jwt), id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        applicationService.delete(userId(jwt), id);
    }

    // The owner always comes from the signed token, never from anything the client sends.
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
