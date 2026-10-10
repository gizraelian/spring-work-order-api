package com.georgeizraelian.workorder.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
public class EnvironmentSafetyConfig {

    private static void require(Environment env, String key, String expected) {
        if (!expected.equals(env.getProperty(key))) throw new IllegalStateException("Unsafe local-browser setting: " + key);
    }

    private static void validateBrowser(Environment env) {
        java.util.Set<String> profiles = java.util.Set.of(env.getActiveProfiles());
        if (!(profiles.equals(java.util.Set.of("dev", "local-browser"))
                || profiles.equals(java.util.Set.of("test", "local-browser")))) {
            throw new IllegalStateException("local-browser requires exclusively dev or test; prod cannot be combined");
        }
        for (String key : new String[]{"APP_USERNAME", "APP_PASSWORD", "app.security.username", "app.security.password"}) {
            String value = env.getProperty(key);
            if (value == null || value.isBlank()) throw new IllegalStateException("Explicit local-browser credentials required: " + key);
        }
        require(env, "server.servlet.session.cookie.http-only", "true");
        require(env, "server.servlet.session.cookie.same-site", "strict");
        require(env, "server.servlet.session.cookie.name", "M4CSESSION");
        require(env, "server.servlet.session.cookie.path", "/api");
        require(env, "server.servlet.session.cookie.max-age", "-1s");
        if (!env.getProperty("server.servlet.session.cookie.domain", "").isBlank()) {
            throw new IllegalStateException("local-browser session cookie must be host-only");
        }
        require(env, "server.servlet.session.tracking-modes", "cookie");
        require(env, "server.servlet.session.persistent", "false");
        require(env, "server.forward-headers-strategy", "none");
        boolean localHttp = env.getProperty("app.local-browser.allow-loopback-http", Boolean.class, false);
        require(env, "server.servlet.session.cookie.secure", localHttp ? "false" : "true");
        if (!localHttp) require(env, "server.ssl.enabled", "true");
        boolean container = env.getProperty("app.local-browser.compose-isolated", Boolean.class, false);
        require(env, "server.address", container ? "0.0.0.0" : "127.0.0.1");
        if (!profiles.contains("test")) {
            if (container) require(env, "spring.datasource.url", "jdbc:postgresql://db:5432/workorder");
            else if (!env.getProperty("spring.datasource.url", "").matches("jdbc:postgresql://127\\.0\\.0\\.1(?::[0-9]+)?/workorder")) {
                throw new IllegalStateException("local-browser requires isolated loopback PostgreSQL");
            }
        }
        java.time.Duration timeout = org.springframework.boot.convert.DurationStyle.detectAndParse(env.getProperty("server.servlet.session.timeout", "0s"));
        if (timeout.compareTo(java.time.Duration.ofSeconds(1)) < 0 || timeout.compareTo(java.time.Duration.ofMinutes(15)) > 0) {
            throw new IllegalStateException("local-browser session timeout must be between 1s and 15m");
        }
    }

    @Bean
    static BeanFactoryPostProcessor environmentSafetyGuard(Environment environment) {
        return beanFactory -> {
            boolean browser = environment.acceptsProfiles(Profiles.of("local-browser"));
            boolean browserEnabled = environment.getProperty("app.local-browser.enabled", Boolean.class, false);
            if (browser != browserEnabled) throw new IllegalStateException("local-browser profile and explicit enablement must agree");
            if (browser) validateBrowser(environment);
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
