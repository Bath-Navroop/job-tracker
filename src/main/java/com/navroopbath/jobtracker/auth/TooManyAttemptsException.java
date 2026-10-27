package com.navroopbath.jobtracker.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class TooManyAttemptsException extends ErrorResponseException {

    TooManyAttemptsException(String detail, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS,
                ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, detail),
                null);
        // Retry-After is in whole seconds. Rounding up so a client never retries too early.
        long seconds = Math.max(1, Math.ceilDiv(retryAfter.toNanos(), 1_000_000_000L));
        getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
    }
}
