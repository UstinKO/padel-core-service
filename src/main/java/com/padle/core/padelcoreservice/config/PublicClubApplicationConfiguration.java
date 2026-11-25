package com.padle.core.padelcoreservice.config;

import com.padle.core.application.clubs.PublicClubQuery;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(PublicClubQuery.class)
public class PublicClubApplicationConfiguration {
}
