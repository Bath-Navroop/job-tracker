package com.navroopbath.jobtracker.application;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateApplicationRequest(
        @NotBlank @Size(max = 200) String company,
        @NotBlank @Size(max = 200) String jobTitle,
        @Size(max = 2048)
        @Pattern(regexp = "https?://\\S+", flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "must be an http or https link")
        String postingUrl,
        ApplicationStatus status,
        LocalDate appliedOn,
        @Size(max = 10_000) String notes) {
}
