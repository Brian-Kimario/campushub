package com.campushub.infrastructure.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start when the configuration fails {@link ConfigurationPolicy}.
 * Runs before any bean is created, so no database connection or migration is
 * attempted with a placeholder credential.
 */
@Component
public class ConfigurationGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        List<String> violations = ConfigurationPolicy.violations(
                environment::getProperty,
                new HashSet<>(Arrays.asList(environment.getActiveProfiles())));
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start. Fix the configuration: " + String.join("; ", violations));
        }
    }
}
