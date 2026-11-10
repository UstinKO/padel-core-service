package com.padle.core.padelcoreservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class PageModelAdviceTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(
                        new PageModelAdvice("https://example.org/"))
                .build();
    }

    @Test
    void shouldRedirectToTargetWithQueryParamExactly() throws Exception {
        mockMvc.perform(get("/test/redirect"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/target?source=test"));
    }

    @Test
    void shouldAddNormalizedSiteBaseUrlToModel() throws Exception {
        mockMvc.perform(get("/test/page"))
                .andExpect(status().isOk())
                .andExpect(model().attribute(
                        "siteBaseUrl", "https://example.org"))
                .andExpect(view().name("test-page"))
                .andExpect(model().attribute("pageTitle", "Test page"));
    }

    @Test
    void shouldReturnStatusResponse() throws Exception {
        mockMvc.perform(get("/test/status"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"status":"ok"}
                        """));
    }

    @Test
    void shouldNotAddSiteBaseUrlForResponseBodyMethod() throws Exception {
        var pageModel = new ExtendedModelMap();
        var request = new MockHttpServletRequest();
        var handler = new HandlerMethod(
                new TestController(),
                TestController.class.getDeclaredMethod("getStatus"));

        request.setAttribute(
                HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, handler);

        new PageModelAdvice("https://example.org/")
                .addSiteBaseUrl(pageModel, request);

        assertThat(pageModel.containsAttribute("siteBaseUrl")).isFalse();
    }

    @Test
    void shouldNotAddSiteBaseUrlForRestController() throws Exception {
        var pageModel = new ExtendedModelMap();
        var request = new MockHttpServletRequest();
        var handler = new HandlerMethod(
                new TestRestController(),
                TestRestController.class.getDeclaredMethod("getStatus"));

        request.setAttribute(
                HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, handler);

        new PageModelAdvice("https://example.org/")
                .addSiteBaseUrl(pageModel, request);

        assertThat(pageModel.containsAttribute("siteBaseUrl")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "https://example.org, https://example.org",
            "https://example.org/, https://example.org",
            "https://example.org///, https://example.org",
            "http://localhost:8080/padel/, http://localhost:8080/padel",
            "http://localhost:8080/padel///sub/, http://localhost:8080/padel///sub"
    })
    void shouldNormalizeUrl(String inputUrl, String expectedUrl) throws NoSuchMethodException {
        var pageModel = new ExtendedModelMap();
        var request = new MockHttpServletRequest();
        var handler = new HandlerMethod(
                new TestController(),
                TestController.class.getDeclaredMethod("page"));

        request.setAttribute(
                HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, handler);

        new PageModelAdvice(inputUrl).addSiteBaseUrl(pageModel, request);

        assertThat(pageModel.getAttribute("siteBaseUrl")).isEqualTo(expectedUrl);
    }

    @Test
    void shouldUseConfiguredBaseUrlRegardlessOfHost() throws Exception {
        mockMvc.perform(get("/test/page")
                        .header("Host", "other.example")
                        .header("X-Forwarded-Host", "other.example"))
                .andExpect(status().isOk())
                .andExpect(model().attribute(
                        "siteBaseUrl", "https://example.org"));
    }

    @Test
    void shouldUseBaseUrlFromSpringConfiguration() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context, "app.base-url=https://configured.example/padel///");

            context.register(PageModelAdvice.class);
            context.refresh();

            var configuredMvc = MockMvcBuilders
                    .standaloneSetup(new TestController())
                    .setControllerAdvice(context.getBean(PageModelAdvice.class))
                    .build();

            configuredMvc.perform(get("/test/page"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute(
                            "siteBaseUrl", "https://configured.example/padel"));
        }
    }

    @Test
    void shouldPreserveRedirectViewLocation() throws Exception {
        mockMvc.perform(get("/test/redirect-view"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/target?source=test"));
    }

    record StatusResponse(String status) {
    }

    @Controller
    static class TestController {

        @GetMapping("/test/redirect")
        public String redirect() {
            return "redirect:/target?source=test";
        }

        @GetMapping("/test/page")
        public ModelAndView page() {
            return new ModelAndView("test-page", "pageTitle", "Test page");
        }

        @ResponseBody
        @GetMapping("/test/status")
        public StatusResponse getStatus() {
            return new StatusResponse("ok");
        }

        @GetMapping("/test/redirect-view")
        public RedirectView redirectView() {
            return new RedirectView("/target?source=test");
        }
    }

    @RestController
    static class TestRestController {

        @GetMapping("/test/rest/status")
        public StatusResponse getStatus() {
            return new StatusResponse("ok");
        }
    }
}
