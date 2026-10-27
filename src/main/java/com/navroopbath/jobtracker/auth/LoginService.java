package com.navroopbath.jobtracker.auth;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.navroopbath.jobtracker.user.User;
import com.navroopbath.jobtracker.user.UserRepository;

@Service
class LoginService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthRateLimiter rateLimiter;
    private final String hashForUnknownEmails;

    LoginService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService,
            AuthRateLimiter rateLimiter) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.hashForUnknownEmails = passwordEncoder.encode("placeholder for emails with no account");
    }

    String login(String email, String password, String clientAddress) {
        String normalizedEmail = User.normalizeEmail(email);
        // Runs before the database lookup and the deliberately slow password check.
        rateLimiter.useLoginAttempt(clientAddress, normalizedEmail);

        Optional<User> user = users.findByEmail(normalizedEmail);
        // Check a password even when the email is unknown, so the response time
        // does not reveal which emails have accounts.
        String hash = user.map(User::getPasswordHash).orElse(hashForUnknownEmails);
        boolean passwordMatches = passwordEncoder.matches(password, hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        rateLimiter.loginSucceeded(normalizedEmail);
        return tokenService.issueFor(user.get());
    }
}
