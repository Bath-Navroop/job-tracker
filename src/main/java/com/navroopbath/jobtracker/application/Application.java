package com.navroopbath.jobtracker.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long userId;

    private String company;

    private String jobTitle;

    private String postingUrl;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    private LocalDate appliedOn;

    private String notes;

    // Declared with the same type name the migration uses, so the startup schema check matches it exactly.
    @Column(columnDefinition = "timestamptz", updatable = false)
    private Instant createdAt;

    @Column(columnDefinition = "timestamptz")
    private Instant updatedAt;

    protected Application() {
    }

    public Application(Long userId, String company, String jobTitle, String postingUrl,
            ApplicationStatus status, LocalDate appliedOn, String notes) {
        this.userId = userId;
        this.company = company;
        this.jobTitle = jobTitle;
        this.postingUrl = postingUrl;
        this.status = status;
        this.appliedOn = appliedOn;
        this.notes = notes;
    }

    void update(String company, String jobTitle, String postingUrl,
            ApplicationStatus status, LocalDate appliedOn, String notes) {
        this.company = company;
        this.jobTitle = jobTitle;
        this.postingUrl = postingUrl;
        this.status = status;
        this.appliedOn = appliedOn;
        this.notes = notes;
    }

    @PrePersist
    void onCreate() {
        createdAt = now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = now();
    }

    // PostgreSQL keeps microseconds, so rounding here makes the returned value match the stored one.
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCompany() {
        return company;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getPostingUrl() {
        return postingUrl;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public LocalDate getAppliedOn() {
        return appliedOn;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
