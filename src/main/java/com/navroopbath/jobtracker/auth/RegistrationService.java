package com.navroopbath.jobtracker.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.navroopbath.jobtracker.user.User;
import com.navroopbath.jobtracker.user.UserRepository;

@Service
class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    RegistrationService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    User register(String email, String password) {
        String normalizedEmail = User.normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }
        try {
            return users.save(new User(normalizedEmail, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            // Another request registered the same email between the check and the insert.
            throw new EmailAlreadyRegisteredException();
        }
    }
}
