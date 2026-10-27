package com.navroopbath.jobtracker.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.navroopbath.jobtracker.user.User;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;

    AuthController(RegistrationService registrationService, LoginService loginService) {
        this.registrationService = registrationService;
        this.loginService = loginService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        User user = registrationService.register(request.email(), request.password(), clientAddress(httpRequest));
        return new UserResponse(user.getId(), user.getEmail());
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String token = loginService.login(request.email(), request.password(), clientAddress(httpRequest));
        return new TokenResponse(token, "Bearer", TokenService.TOKEN_LIFETIME.toSeconds());
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new UserResponse(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("email"));
    }

    // Behind the production proxy, Tomcat has already replaced the remote address with the
    // client's address from X-Forwarded-For.
    private static String clientAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
