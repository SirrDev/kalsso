package com.footbooking.auth;

import com.footbooking.auth.RateLimiter.Decision;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private final RateLimiter limiter = Mockito.mock(RateLimiter.class);
    private final RateLimitFilter filter = new RateLimitFilter(limiter, true, 5, 5);

    private MockHttpServletResponse call(String method, String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void allowedRequest_passes() throws Exception {
        when(limiter.tryAcquire(startsWith("login:"), anyInt(), any())).thenReturn(new Decision(true, 0));

        assertEquals(200, call("POST", "/api/auth/login").getStatus());
    }

    @Test
    void refusedRequest_gets429WithRetryAfter() throws Exception {
        when(limiter.tryAcquire(startsWith("login:"), anyInt(), any())).thenReturn(new Decision(false, 42));

        MockHttpServletResponse res = call("POST", "/api/auth/login");

        assertEquals(429, res.getStatus());
        assertEquals("42", res.getHeader("Retry-After"));
        assertTrue(res.getContentAsString().contains("\"status\":429"));
    }

    @Test
    void otherRoutesAndMethods_areNotLimited() throws Exception {
        assertEquals(200, call("GET", "/api/auth/login").getStatus());
        assertEquals(200, call("POST", "/api/bookings").getStatus());
        Mockito.verifyNoInteractions(limiter);
    }

    @Test
    void disabled_neverLimits() throws Exception {
        RateLimitFilter off = new RateLimitFilter(limiter, false, 5, 5);
        MockHttpServletResponse res = new MockHttpServletResponse();
        off.doFilter(new MockHttpServletRequest("POST", "/api/auth/login"), res, new MockFilterChain());

        assertEquals(200, res.getStatus());
        Mockito.verifyNoInteractions(limiter);
    }
}