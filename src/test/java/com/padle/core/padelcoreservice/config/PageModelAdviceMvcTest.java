package com.padle.core.padelcoreservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(PageModelAdviceMvcTest.MvcConfig.class)
@TestPropertySource(properties =
        "app.base-url=https://configured.example/padel///")
class PageModelAdviceMvcTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void shouldAddConfiguredBaseUrlToPage() throws Exception {
        mockMvc.perform(get("/test/page"))
                .andExpect(status().isOk())
                .andExpect(view().name("test-page"))
                .andExpect(model().attribute("pageTitle", "Test page"))
                .andExpect(model().attribute(
                        "siteBaseUrl", "https://configured.example/padel"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/test/redirect", "/test/redirect-view"})
    void shouldPreserveRedirectLocation(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/target?source=test"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({
            PageModelAdvice.class,
            PageModelAdviceTest.TestController.class
    })
    static class MvcConfig {
    }
}
