package com.padle.core.padelcoreservice.security;

import tools.jackson.databind.ObjectMapper;
import org.springframework.context.support.ResourceBundleMessageSource;

final class AccountTestResponses {
    static AccountResponses create() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        return new AccountResponses(new ObjectMapper(), messages);
    }
}
