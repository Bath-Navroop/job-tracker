package com.navroopbath.jobtracker.auth;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

/**
 * Limits login and registration attempts with token buckets. Login has two limits: one per client
 * address, which every attempt uses, and one per email, which only failed attempts use up.
 * Registration has one per client address.
 */
@Component
class AuthRateLimiter {

    private static final String LOGIN_LIMIT_REACHED = "Too many login attempts. Try again later.";
    private static final String REGISTRATION_LIMIT_REACHED = "Too many registration attempts. Try again later.";

    private final Buckets loginAddresses;
    private final Buckets loginEmails;
    private final Buckets registrationAddresses;

    AuthRateLimiter(RateLimitProperties properties) {
        this.loginAddresses = new Buckets(properties.loginPerIp());
        this.loginEmails = new Buckets(properties.loginPerEmail());
        this.registrationAddresses = new Buckets(properties.registrationPerIp());
    }

    /**
     * Uses one login attempt from the address and one from the email, or throws if either has none left.
     */
    void useLoginAttempt(String clientAddress, String email) {
        take(loginAddresses.forKey(clientAddress), LOGIN_LIMIT_REACHED);
        take(loginEmails.forKey(email), LOGIN_LIMIT_REACHED);
    }

    /**
     * Gives the email its attempt back, so only failed logins count against it.
     */
    void loginSucceeded(String email) {
        loginEmails.giveBack(email);
    }

    /**
     * Uses one registration attempt from the address, or throws if it has none left.
     */
    void useRegistrationAttempt(String clientAddress) {
        take(registrationAddresses.forKey(clientAddress), REGISTRATION_LIMIT_REACHED);
    }

    private static void take(Bucket bucket, String limitReachedMessage) {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new TooManyAttemptsException(limitReachedMessage,
                    Duration.ofNanos(probe.getNanosToWaitForRefill()));
        }
    }

    /**
     * One token bucket per key (a client address or an email), all with the same limit.
     */
    private static final class Buckets {

        // Keeps memory bounded even when requests come from a flood of different addresses or emails.
        private static final long MAX_BUCKETS = 10_000;

        private final RateLimitProperties.Limit limit;
        private final Cache<String, Bucket> cache;

        Buckets(RateLimitProperties.Limit limit) {
            this.limit = limit;
            // A bucket nobody has used for one period is full again, so forgetting it then changes nothing.
            this.cache = Caffeine.newBuilder()
                    .expireAfterAccess(limit.period())
                    .maximumSize(MAX_BUCKETS)
                    .build();
        }

        Bucket forKey(String key) {
            return cache.get(key, unused -> Bucket.builder()
                    .addLimit(bandwidth -> bandwidth.capacity(limit.attempts())
                            .refillGreedy(limit.attempts(), limit.period()))
                    .build());
        }

        void giveBack(String key) {
            Bucket bucket = cache.getIfPresent(key);
            if (bucket != null) {
                bucket.addTokens(1);
            }
        }
    }
}
