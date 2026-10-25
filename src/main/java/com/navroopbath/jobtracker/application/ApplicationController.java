package com.navroopbath.jobtracker.application;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    @ResponseStatus(HttpStatus.CREATED)
    ApplicationResponse create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateApplicationRequest request) {
        return ApplicationResponse.from(applicationService.create(userId(jwt), request));
    }

    @GetMapping
    List<ApplicationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return applicationService.listFor(userId(jwt)).stream()
                .map(ApplicationResponse::from)
                .toList();
    }

    // The owner always comes from the signed token, never from anything the client sends.
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
