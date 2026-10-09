package com.footbooking.auth;

import com.footbooking.auth.RateLimiter.Decision;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/** Limite le debit de la connexion et de l'inscription, par adresse IP. Ne journalise jamais l'IP. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiter limiter;
    private final boolean enabled;
    private final int loginLimit;
    private final Duration loginWindow;
    private final int registerLimit;
    private final Duration registerWindow;

    public RateLimitFilter(RateLimiter limiter,
                           @Value("${app.rate-limit.enabled:true}") boolean enabled,
                           @Value("${app.rate-limit.login-per-minute:5}") int loginLimit,
                           @Value("${app.rate-limit.register-per-hour:5}") int registerLimit) {
        this.limiter = limiter;
        this.enabled = enabled;
        this.loginLimit = loginLimit;
        this.loginWindow = Duration.ofMinutes(1);
        this.registerLimit = registerLimit;
        this.registerWindow = Duration.ofHours(1);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled || !"POST".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !path.equals("/api/auth/login") && !path.equals("/api/auth/register");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean login = request.getRequestURI().equals("/api/auth/login");
        String key = (login ? "login:" : "register:") + request.getRemoteAddr();
        Decision d = login
                ? limiter.tryAcquire(key, loginLimit, loginWindow)
                : limiter.tryAcquire(key, registerLimit, registerWindow);
        if (d.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Limite de debit atteinte sur {}", login ? "login" : "register");
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(d.retryAfterSeconds()));
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":429,"
                + "\"error\":\"Trop de tentatives, reessayez plus tard\",\"details\":{}}");
    }
}