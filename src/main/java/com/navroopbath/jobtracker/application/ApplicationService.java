package com.navroopbath.jobtracker.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // Matching on id and owner in one query makes someone else's application look exactly like a missing one.
    Application get(Long userId, Long id) {
        return applications.findByIdAndUserId(id, userId)
                .orElseThrow(ApplicationNotFoundException::new);
    }

    // No save() needed: Hibernate tracks the loaded entity and writes the changed columns when the transaction commits.
    @Transactional
    Application update(Long userId, Long id, UpdateApplicationRequest request) {
        Application application = get(userId, id);
        application.update(
                request.company().strip(),
                request.jobTitle().strip(),
                request.postingUrl(),
                request.status(),
                request.appliedOn(),
                request.notes());
        return application;
    }

    @Transactional
    void delete(Long userId, Long id) {
        applications.delete(get(userId, id));
    }
}
