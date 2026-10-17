package com.dolbaia.ledger.auth;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dolbaia.ledger.common.ApiException;
import com.dolbaia.ledger.support.Xsrf;
import com.dolbaia.ledger.common.HealthController;

/** The auth slice through the real security chain, with the database mocked out. */
@WebMvcTest(controllers = {AuthController.class, HealthController.class})
@Import({SecurityConfig.class, SessionTokenService.class, SessionCookies.class, CredentialVerifier.class,
        AuthWebTest.Config.class})
class AuthWebTest {

    static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode("correct horse");

    /** Slices skip @ConfigurationPropertiesScan, so bind AuthProperties here. */
    @TestConfiguration
    @EnableConfigurationProperties(AuthProperties.class)
    static class Config {}

    @Autowired MockMvc mvc;
    @Autowired SessionTokenService tokens;
    @MockitoBean LoginRateLimiter rateLimiter;
    @MockitoBean JdbcTemplate jdbc;

    @DynamicPropertySource
    static void auth(DynamicPropertyRegistry registry) {
        registry.add("app.auth.username", () -> "ledger-user");
        registry.add("app.auth.password-hash", () -> PASSWORD_HASH);
        registry.add("app.auth.session-secret", () -> "a-test-secret-that-is-longer-than-32-characters");
        registry.add("app.auth.secure-cookie", () -> "true");
    }

    static String login(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void loginSetsTheSessionCookie() throws Exception {
        mvc.perform(post("/api/auth/login").with(Xsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON).content(login(" ledger-user ", "correct horse")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ledger-user"))
                .andExpect(cookie().httpOnly(SessionCookies.NAME, true))
                .andExpect(cookie().secure(SessionCookies.NAME, true))
                .andExpect(cookie().sameSite(SessionCookies.NAME, "Lax"))
                .andExpect(cookie().maxAge(SessionCookies.NAME, 14 * 24 * 60 * 60));

        verify(rateLimiter).recordSuccess(eq("ledger-user"), anyString());
    }

    @Test
    void wrongPasswordIsRejectedAndRecorded() throws Exception {
        mvc.perform(post("/api/auth/login").with(Xsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON).content(login("ledger-user", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"))
                .andExpect(cookie().doesNotExist(SessionCookies.NAME));

        verify(rateLimiter).recordFailure(eq("ledger-user"), anyString());
    }

    @Test
    void rateLimitedLoginsNeverCheckThePassword() throws Exception {
        doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_attempts", "Slow down."))
                .when(rateLimiter).check(anyString(), anyString());

        mvc.perform(post("/api/auth/login").with(Xsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON).content(login("ledger-user", "correct horse")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("too_many_attempts"));

        verify(rateLimiter, never()).recordSuccess(anyString(), anyString());
    }

    @Test
    void blankFieldsFailValidation() throws Exception {
        mvc.perform(post("/api/auth/login").with(Xsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON).content(login("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    void loginWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(login("ledger-user", "correct horse")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void csrfEndpointIssuesTheXsrfCookie() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cookieName").value("XSRF-TOKEN"))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void apiNeedsASession() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    void aValidCookieAuthenticates() throws Exception {
        mvc.perform(get("/api/auth/me").cookie(new Cookie(SessionCookies.NAME, tokens.create("ledger-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ledger-user"));
    }

    @Test
    void aTamperedCookieDoesNot() throws Exception {
        mvc.perform(get("/api/auth/me").cookie(new Cookie(SessionCookies.NAME, tokens.create("ledger-user") + "x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsTheCookie() throws Exception {
        mvc.perform(post("/api/auth/logout").with(Xsrf.token(mvc)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(SessionCookies.NAME, 0));
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mvc.perform(get("/api/auth/csrf").secure(true))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    void healthIsPublicAndReportsTheDatabase() throws Exception {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.database").value("connected"));
    }

    @Test
    void healthIs503WithoutTheDatabase() throws Exception {
        when(jdbc.queryForObject(any(String.class), eq(Integer.class)))
                .thenThrow(new CannotGetJdbcConnectionException("down"));

        mvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("degraded"))
                .andExpect(jsonPath("$.database").value("unavailable"));
    }
}
