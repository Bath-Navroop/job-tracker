package com.navroopbath.jobtracker.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits for login and registration attempts, set under {@code auth.rate-limit}. Each limit allows
 * {@code attempts} in a row, then refills at {@code attempts} per {@code period}.
 */
@ConfigurationProperties("auth.rate-limit")
public record RateLimitProperties(Limit loginPerIp, Limit loginPerEmail, Limit registrationPerIp) {

    public record Limit(long attempts, Duration period) {
    }
}
