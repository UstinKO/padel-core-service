package com.padle.core.padelcoreservice.security;

import com.google.common.base.Ticker;
import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;

class AccountRateLimitTest {
    private final AtomicLong time = new AtomicLong(1_700_000_000_000L);
    private RateLimitFilter filter;
    @BeforeEach void setup() {
        TimeMeter meter = new TimeMeter() {
            public long currentTimeNanos() { return time.get() * 1_000_000; }
            public boolean isWallClockBased() { return true; }
        };
        Ticker ticker = new Ticker() { public long read() { return time.get() * 1_000_000; } };
        filter = new RateLimitFilter(AccountTestResponses.create(), time::get, meter, ticker);
        ReflectionTestUtils.setField(filter, "whitelistedIpsProperty", "");
        ReflectionTestUtils.invokeMethod(filter, "initWhitelistedIps");
    }
    private MockHttpServletResponse request(String method, String path, int downstream) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.addHeader("Accept", "application/json");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(downstream));
        return response;
    }
    @Test void readsAndHandledValidationDoNotEscalateSharedIp() throws Exception {
        for (int i=0;i<12;i++) assertThat(request("GET", "/login", 200).getStatus()).isEqualTo(200);
        for (int i=0;i<12;i++) assertThat(request("GET", "/api/auth/session", 200).getStatus()).isEqualTo(200);
        for (int i=0;i<3;i++) assertThat(request("POST", "/api/players/registro", 422).getStatus()).isEqualTo(422);
        assertThat(request("GET", "/api/auth/session", 200).getStatus()).isEqualTo(200);
        time.addAndGet(60_000);
        assertThat(request("POST", "/api/players/registro", 201).getStatus()).isEqualTo(201);
    }
    @Test void bucketRetryIsRemainingTimeAndJsonLoginNeverRedirects() throws Exception {
        for(int i=0;i<10;i++) request("POST", "/login", 401);
        time.addAndGet(20_000);
        var response = request("POST", "/login", 401);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("40");
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(response.getContentAsString()).contains("RATE_LIMITED", "blockedUntil");
    }
    @Test void sustainedFloodBlocksAndBlockedRetryDecreases() throws Exception {
        for(int i=0;i<15;i++) request("POST", "/login", 401);
        time.addAndGet(30_000);
        assertThat(request("GET", "/api/auth/session", 200).getHeader("Retry-After")).isEqualTo("870");
        time.addAndGet(870_001);
        assertThat(request("GET", "/api/auth/session", 200).getStatus()).isEqualTo(200);
    }
}
