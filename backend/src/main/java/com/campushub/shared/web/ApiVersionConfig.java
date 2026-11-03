package com.campushub.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Mounts the application controllers under {@code /api/v1} (API_CONTRACT header). Controllers keep
 * their relative mappings, so the prefix is applied once here and never repeated in a mapping.
 * Actuator and API documentation endpoints live outside this package and keep their own paths.
 */
@Configuration
class ApiVersionConfig implements WebMvcConfigurer {

    static final String API_PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX,
                HandlerTypePredicate.forBasePackage("com.campushub")
                        .and(HandlerTypePredicate.forAnnotation(RestController.class)));
    }
}
