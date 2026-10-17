package com.dolbaia.ledger.auth;

import java.security.Principal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dolbaia.ledger.common.ApiException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(
            @NotBlank @Size(max = 200) String username,
            @NotBlank @Size(max = 1000) String password) {}

    public record SessionUser(String username) {}

    public record CsrfResponse(String cookieName, String headerName) {}

    static final String CSRF_COOKIE = "XSRF-TOKEN";

    private final CredentialVerifier credentials;
    private final SessionTokenService tokens;
    private final SessionCookies cookies;
    private final LoginRateLimiter rateLimiter;

    public AuthController(CredentialVerifier credentials, SessionTokenService tokens,
                          SessionCookies cookies, LoginRateLimiter rateLimiter) {
        this.credentials = credentials;
        this.tokens = tokens;
        this.cookies = cookies;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    ResponseEntity<SessionUser> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        String username = body.username().trim();
        String ip = request.getRemoteAddr();
        rateLimiter.check(username, ip);

        if (!credentials.verify(username, body.password())) {
            rateLimiter.recordFailure(username, ip);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials",
                    "The username or password is incorrect.");
        }

        rateLimiter.recordSuccess(username, ip);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(tokens.create(username)).toString())
                .body(new SessionUser(username));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    @GetMapping("/me")
    SessionUser me(Principal principal) {
        return new SessionUser(principal.getName());
    }

    /**
     * Reading the token makes Spring Security write the XSRF-TOKEN cookie. The
     * SPA calls this once before its first POST (including login) and then
     * echoes the cookie's value in the X-XSRF-TOKEN header.
     */
    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        token.getToken();
        return new CsrfResponse(CSRF_COOKIE, token.getHeaderName());
    }
}
