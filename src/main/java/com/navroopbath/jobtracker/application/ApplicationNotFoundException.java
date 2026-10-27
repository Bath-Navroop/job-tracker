package com.navroopbath.jobtracker.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class ApplicationNotFoundException extends ErrorResponseException {

    ApplicationNotFoundException() {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Application not found."),
                null);
    }
}
