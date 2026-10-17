package com.dolbaia.ledger.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The real double-submit CSRF flow the SPA uses: fetch the XSRF-TOKEN cookie,
 * send it back with its value in X-XSRF-TOKEN. Used instead of
 * spring-security-test's csrf(), which swaps the token repository of the
 * cached context.
 */
public final class Xsrf {

    private Xsrf() {}

    public static RequestPostProcessor token(MockMvc mvc) throws Exception {
        Cookie cookie = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) throw new IllegalStateException("No XSRF-TOKEN cookie was issued.");
        return request -> {
            Cookie[] existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
            Cookie[] cookies = java.util.Arrays.copyOf(existing, existing.length + 1);
            cookies[existing.length] = cookie;
            request.setCookies(cookies);
            request.addHeader("X-XSRF-TOKEN", cookie.getValue());
            return request;
        };
    }
}
