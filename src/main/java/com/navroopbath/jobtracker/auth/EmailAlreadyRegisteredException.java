package com.navroopbath.jobtracker.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class EmailAlreadyRegisteredException extends ErrorResponseException {

    EmailAlreadyRegisteredException() {
        super(HttpStatus.CONFLICT,
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                        "An account with this email already exists."),
                null);
    }
}
