package com.alejandro.mtoconfiguration.core.exception.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ApiErrorProperties.class)
public class ApiErrorConfiguration {
}
