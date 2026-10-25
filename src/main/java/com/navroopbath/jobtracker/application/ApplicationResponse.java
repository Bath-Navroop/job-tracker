package com.navroopbath.jobtracker.application;

import java.time.Instant;
import java.time.LocalDate;

public record ApplicationResponse(
        Long id,
        String company,
        String jobTitle,
        String postingUrl,
        ApplicationStatus status,
        LocalDate appliedOn,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getCompany(),
                application.getJobTitle(),
                application.getPostingUrl(),
                application.getStatus(),
                application.getAppliedOn(),
                application.getNotes(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }
}
