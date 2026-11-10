package com.padle.core.padelcoreservice.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

// app.security.whitelisted-ips без дефолта в коде — без переменной окружения
// список доверенных IP должен быть пуст, а не захардкоженные IP прода.
class RateLimitFilterWhitelistTest {

    private RateLimitFilter newFilter(String whitelistedIpsProperty) {
        RateLimitFilter filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "whitelistedIpsProperty", whitelistedIpsProperty);
        ReflectionTestUtils.invokeMethod(filter, "initWhitelistedIps");
        return filter;
    }

    @Test
    void initWhitelistedIps_sinVariableDeEntorno_resultaEnListaVacia() {
        RateLimitFilter filter = newFilter("");

        Set<String> whitelistedIps = (Set<String>) ReflectionTestUtils.getField(filter, "whitelistedIps");

        assertThat(whitelistedIps).isEmpty();
    }

    @Test
    void initWhitelistedIps_conVariableDeEntorno_contieneExactamenteEsasIps() {
        RateLimitFilter filter = newFilter(" 1.2.3.4 , 5.6.7.8 ");

        Set<String> whitelistedIps = (Set<String>) ReflectionTestUtils.getField(filter, "whitelistedIps");

        assertThat(whitelistedIps).containsExactlyInAnyOrder("1.2.3.4", "5.6.7.8");
    }

    @Test
    void doFilterInternal_sinVariableDeEntorno_noDaViaLibreATodosLosIps() throws Exception {
        RateLimitFilter filter = newFilter("");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/css/style.css");
        request.addHeader("X-Real-IP", "194.124.210.113"); // адрес из прежнего захардкоженного allowlist
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> invoked.set(true);

        ReflectionTestUtils.invokeMethod(filter, "doFilterInternal", request, response, chain);

        // пропускает дальше, но из-за classifyPath (ресурс без лимита), не из-за whitelist
        assertThat(invoked).isTrue();
        assertThat((Set<String>) ReflectionTestUtils.getField(filter, "whitelistedIps")).doesNotContain("194.124.210.113");
    }

    @Test
    void doFilterInternal_conIpEnWhitelist_pasaSinAplicarLimite() throws Exception {
        RateLimitFilter filter = newFilter("9.9.9.9");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/tournaments");
        request.addHeader("X-Real-IP", "9.9.9.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> invoked.set(true);

        ReflectionTestUtils.invokeMethod(filter, "doFilterInternal", request, response, chain);

        assertThat(invoked).isTrue();
    }

    @Test
    void doFilterInternal_webAssetNoConsumeApplicationBucket() throws Exception {
        RateLimitFilter filter = newFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/web/assets/app.hash.js");
        request.addHeader("X-Real-IP", "203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> invoked.set(true);

        ReflectionTestUtils.invokeMethod(filter, "doFilterInternal", request, response, chain);

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
