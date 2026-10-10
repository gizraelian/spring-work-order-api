package com.georgeizraelian.workorder.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
public class EnvironmentSafetyConfig {

    @Bean
    static BeanFactoryPostProcessor environmentSafetyGuard(Environment environment) {
        return beanFactory -> {
            boolean production = environment.acceptsProfiles(Profiles.of("prod"));
            boolean developmentOrTest = environment.acceptsProfiles(
                    Profiles.of("dev", "test", "postgres-integration"));
            if (production && developmentOrTest) {
                throw new IllegalStateException("prod cannot be combined with development or test profiles");
            }
            if (!developmentOrTest) {
                for (String key : new String[]{"spring.datasource.url", "spring.datasource.username",
                        "spring.datasource.password", "app.security.username", "app.security.password"}) {
                    String value = environment.getProperty(key);
                    if (value == null || value.isBlank()) {
                        // Name the missing setting only; never include a credential value in an error.
                        throw new IllegalStateException("Explicit production configuration required: " + key);
                    }
                }
                if (environment.getProperty("spring.flyway.enabled", Boolean.class, false)
                        && !environment.getProperty("app.migrations.approved", Boolean.class, false)) {
                    throw new IllegalStateException("Production migrations require separate approval: app.migrations.approved");
                }
            }
        };
    }
}
