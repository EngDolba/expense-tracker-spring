package com.dolbaia.ledger.auth;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** The `ledger-session` cookie: HttpOnly, SameSite=Lax, Secure outside local development. */
@Component
public class SessionCookies {

    public static final String NAME = "ledger-session";

    private final AuthProperties properties;

    public SessionCookies(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie issue(String token) {
        return base(token).maxAge(properties.sessionDuration()).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.secureCookie())
                .sameSite("Lax")
                .path("/");
    }
}
