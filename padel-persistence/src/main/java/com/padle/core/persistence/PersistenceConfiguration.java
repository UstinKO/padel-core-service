package com.padle.core.persistence;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@AutoConfigurationPackage
@ComponentScan
public class PersistenceConfiguration {
}
