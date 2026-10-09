package com.padle.core.padelcoreservice.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

// LFPT-0518: requestId в MDC — доступен во время цепочки фильтров, уникален на запрос,
// очищается после (поток Tomcat переиспользуется для следующего запроса).
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void doFilter_ponesRequestIdEnMdcDuranteLaCadenaYLoLimpiaDespues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        String[] seenDuringChain = new String[1];
        doAnswer(invocation -> {
            seenDuringChain[0] = MDC.get(RequestIdFilter.MDC_KEY);
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertThat(seenDuringChain[0]).isNotBlank();
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void doFilter_generaUnIdDistintoParaCadaRequest() throws Exception {
        String[] ids = new String[2];

        MockHttpServletRequest request1 = new MockHttpServletRequest();
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        FilterChain chain1 = mock(FilterChain.class);
        doAnswer(invocation -> {
            ids[0] = MDC.get(RequestIdFilter.MDC_KEY);
            return null;
        }).when(chain1).doFilter(request1, response1);
        filter.doFilter(request1, response1, chain1);

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        FilterChain chain2 = mock(FilterChain.class);
        doAnswer(invocation -> {
            ids[1] = MDC.get(RequestIdFilter.MDC_KEY);
            return null;
        }).when(chain2).doFilter(request2, response2);
        filter.doFilter(request2, response2, chain2);

        assertThat(ids[0]).isNotBlank();
        assertThat(ids[1]).isNotBlank();
        assertThat(ids[0]).isNotEqualTo(ids[1]);
    }
}
