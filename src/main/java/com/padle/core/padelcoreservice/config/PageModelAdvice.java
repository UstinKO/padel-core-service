package com.padle.core.padelcoreservice.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;


@ControllerAdvice
public class PageModelAdvice {

    private final String baseUrl;

    public PageModelAdvice(@Value("${app.base-url}") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @ModelAttribute
    public void addSiteBaseUrl(Model model, HttpServletRequest request) {

        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return;
        }

        if (AnnotatedElementUtils.isAnnotated(handlerMethod.getMethod(), ResponseBody.class)) {
            return;
        }

        if (AnnotatedElementUtils.isAnnotated(handlerMethod.getBeanType(), ResponseBody.class)) {
            return;
        }

        model.addAttribute("siteBaseUrl", this.baseUrl);
    }
}
