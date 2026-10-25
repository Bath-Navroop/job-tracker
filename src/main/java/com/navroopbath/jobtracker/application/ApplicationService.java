package com.navroopbath.jobtracker.application;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
class ApplicationService {

    private final ApplicationRepository applications;

    ApplicationService(ApplicationRepository applications) {
        this.applications = applications;
    }

    Application create(Long userId, CreateApplicationRequest request) {
        ApplicationStatus status = request.status() != null ? request.status() : ApplicationStatus.SAVED;
        return applications.save(new Application(
                userId,
                request.company().strip(),
                request.jobTitle().strip(),
                request.postingUrl(),
                status,
                request.appliedOn(),
                request.notes()));
    }

    List<Application> listFor(Long userId) {
        return applications.findByUserIdOrderByCreatedAtDescIdDesc(userId);
    }
}
