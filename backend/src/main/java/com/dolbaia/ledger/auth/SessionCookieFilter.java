package com.dolbaia.ledger.auth;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

/**
 * Authenticates a request from its session cookie. Not a bean, so it runs only
 * inside the security chain and not a second time as a servlet filter.
 */
public class SessionCookieFilter extends OncePerRequestFilter {

    static final List<SimpleGrantedAuthority> OWNER = List.of(new SimpleGrantedAuthority("ROLE_OWNER"));

    private final SessionTokenService tokens;
    private final SecurityContextHolderStrategy contexts = SecurityContextHolder.getContextHolderStrategy();

    public SessionCookieFilter(SessionTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie cookie = WebUtils.getCookie(request, SessionCookies.NAME);
        if (cookie != null && contexts.getContext().getAuthentication() == null) {
            tokens.verify(cookie.getValue()).ifPresent(username -> {
                SecurityContext context = contexts.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(username, null, OWNER));
                contexts.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
