package com.padle.core.padelcoreservice.config.spa;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpaForwardHandlerMappingTest {

    private final SpaForwardHandlerMapping mapping =
            new SpaForwardHandlerMapping(List.of("/preview", "/preview/{*path}"));

    @ParameterizedTest
    @ValueSource(strings = {"/preview", "/preview/", "/preview/path"})
    void previewRootSlashAndDeepLinkHaveValidMappings(String path) {
        var request = new MockHttpServletRequest("GET", path);
        ServletRequestPathUtils.parseAndCache(request);

        assertThat(mapping.getHandlerInternal(request)).isNotNull();
    }
}
