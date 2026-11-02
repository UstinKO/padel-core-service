package com.padle.core.padelcoreservice.service.marketing;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QrLandingInterceptorTest {
    private final QrStatsService stats = mock(QrStatsService.class);
    private final QrLandingInterceptor interceptor = new QrLandingInterceptor(stats);

    private MockHttpServletRequest landing() {
        var request = new MockHttpServletRequest("GET", "/");
        request.setParameter("utm_source", "black_padel");
        request.setParameter("utm_medium", "qr");
        request.setParameter("utm_campaign", "offline_tournaments");
        return request;
    }

    @Test
    void countsOnlyOnceAfterSuccessfulRenderAndDisablesCache() {
        var request = landing();
        var response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, this);
        verifyNoInteractions(stats);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        interceptor.afterCompletion(request, response, this, null);
        interceptor.afterCompletion(request, response, this, null);
        verify(stats, times(1)).recordLanding("black_padel");
    }

    @Test
    void failedRenderRedirectAndInternalDispatchDoNotCount() {
        for (int status : new int[]{302, 404, 500}) {
            var request = landing();
            var response = new MockHttpServletResponse();
            interceptor.preHandle(request, response, this);
            response.setStatus(status);
            interceptor.afterCompletion(request, response, this, null);
        }
        var request = landing();
        var response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, this);
        interceptor.afterCompletion(request, response, this, new IllegalStateException("render"));
        for (var type : new DispatcherType[]{DispatcherType.FORWARD, DispatcherType.ERROR, DispatcherType.ASYNC}) {
            request = landing();
            request.setDispatcherType(type);
            interceptor.preHandle(request, response, this);
            interceptor.afterCompletion(request, response, this, null);
        }
        verifyNoInteractions(stats);
    }

    @Test
    void ambiguousParametersAndRebyDoNotCount() {
        for (String key : new String[]{"utm_source", "utm_medium", "utm_campaign"}) {
            var request = landing();
            request.setParameter(key, request.getParameter(key), request.getParameter(key));
            assertThat(QrLandingPolicy.source(request.getParameterMap())).isEmpty();
        }
        var request = landing();
        request.setParameter("utm_source", "reby");
        assertThat(QrLandingPolicy.source(request.getParameterMap())).isEmpty();
    }
}
