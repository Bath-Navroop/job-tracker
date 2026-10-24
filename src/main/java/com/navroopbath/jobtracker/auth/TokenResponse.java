package com.navroopbath.jobtracker.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
