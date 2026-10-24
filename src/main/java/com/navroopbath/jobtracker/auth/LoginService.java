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
    private final String hashForUnknownEmails;

    LoginService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.hashForUnknownEmails = passwordEncoder.encode("placeholder for emails with no account");
    }

    String login(String email, String password) {
        Optional<User> user = users.findByEmail(User.normalizeEmail(email));
        // Check a password even when the email is unknown, so the response time
        // does not reveal which emails have accounts.
        String hash = user.map(User::getPasswordHash).orElse(hashForUnknownEmails);
        boolean passwordMatches = passwordEncoder.matches(password, hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return tokenService.issueFor(user.get());
    }
}
