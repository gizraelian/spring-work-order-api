package com.georgeizraelian.workorder.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentSafetyConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnvironmentSafetyConfig.class);
    private final String[] settings = {"spring.datasource.url=jdbc:postgresql://127.0.0.1:1/disposable-only",
            "spring.datasource.username=fixture-user", "spring.datasource.password=fixture-password",
            "app.security.username=fixture-user", "app.security.password=fixture-password"};


    private final String[] browserSettings = {
            "spring.profiles.active=dev,local-browser", "app.local-browser.enabled=true",
            "APP_USERNAME=fixture", "APP_PASSWORD=test-only-password", "app.security.username=fixture",
            "app.security.password=test-only-password", "server.servlet.session.cookie.http-only=true",
            "server.servlet.session.cookie.same-site=strict", "server.servlet.session.cookie.name=M4CSESSION",
            "server.servlet.session.cookie.path=/api", "server.servlet.session.cookie.max-age=-1s", "server.servlet.session.cookie.secure=false",
            "server.servlet.session.tracking-modes=cookie", "server.servlet.session.persistent=false",
            "server.forward-headers-strategy=none", "app.local-browser.allow-loopback-http=true",
            "app.local-browser.compose-isolated=true", "server.address=0.0.0.0",
            "spring.datasource.url=jdbc:postgresql://db:5432/workorder", "server.servlet.session.timeout=15m"};

    @Test void acceptsOnlyExplicitIsolatedBrowserSettings() {
        runner.withPropertyValues(browserSettings).run(context -> assertThat(context).hasNotFailed());
    }
    @Test void browserProfileRequiresConsistentEnablementAndExplicitCredentials() {
        for (String override : new String[]{"app.local-browser.enabled=false", "APP_USERNAME=", "APP_PASSWORD="}) {
            runner.withPropertyValues(browserSettings).withPropertyValues(override)
                    .run(context -> assertThat(context).hasFailed());
        }
        runner.withPropertyValues(settings).withPropertyValues("app.local-browser.enabled=true")
                .run(context -> assertThat(context.getStartupFailure()).hasMessageContaining("must agree"));
    }
    @Test void browserProfileRejectsProductionAndAdditionalProfiles() {
        for (String profile : new String[]{"prod,local-browser", "prod,dev,local-browser", "local-browser", "dev,local-browser,migrated"}) {
            runner.withPropertyValues(browserSettings).withPropertyValues("spring.profiles.active=" + profile)
                    .run(context -> assertThat(context).hasFailed());
        }
    }
    @Test void browserProfileRejectsUnsafeCookieDatabaseAndTransportOverrides() {
        for (String override : new String[]{"server.servlet.session.cookie.http-only=false", "server.servlet.session.cookie.same-site=none",
                "server.servlet.session.cookie.secure=true", "server.servlet.session.cookie.path=/", "server.forward-headers-strategy=framework",
                "spring.datasource.url=jdbc:postgresql://public.example:5432/workorder", "server.servlet.session.timeout=30m",
                "server.servlet.session.cookie.max-age=1d", "server.servlet.session.cookie.domain=public.example", "server.servlet.session.tracking-modes=url", "server.servlet.session.persistent=true"}) {
            runner.withPropertyValues(browserSettings).withPropertyValues(override).run(context -> assertThat(context).hasFailed());
        }
        runner.withPropertyValues(browserSettings).withPropertyValues("app.local-browser.compose-isolated=false")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void browserHttpsRequiresSecureCookiesAndEnabledTls() {
        runner.withPropertyValues(browserSettings).withPropertyValues("app.local-browser.allow-loopback-http=false")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(browserSettings).withPropertyValues("app.local-browser.allow-loopback-http=false",
                "server.servlet.session.cookie.secure=true", "server.ssl.enabled=true")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void productionAndDefaultRequireEveryExplicitCredentialSetting() {
        for (String profile : new String[]{"prod", "default"}) {
            for (int missing = 0; missing < settings.length; missing++) {
                String[] incomplete = settings.clone();
                String key = settings[missing].substring(0, settings[missing].indexOf('='));
                incomplete[missing] = key + "= ";
                runner.withPropertyValues(incomplete).withPropertyValues("spring.profiles.active=" + profile)
                        .run(context -> assertThat(context.getStartupFailure())
                                .hasMessageContaining("Explicit production configuration required: " + key));
            }
        }
    }

    @Test
    void productionRejectsMixedDevelopmentAndTestProfiles() {
        for (String profile : new String[]{"dev", "test", "postgres-integration"}) {
            runner.withPropertyValues(settings).withPropertyValues("spring.profiles.active=prod," + profile)
                    .run(context -> assertThat(context.getStartupFailure())
                            .hasMessageContaining("prod cannot be combined"));
        }
    }

    @Test
    void productionMigrationRequiresExplicitApproval() {
        runner.withPropertyValues(settings)
                .withPropertyValues("spring.profiles.active=prod", "spring.flyway.enabled=true")
                .run(context -> assertThat(context.getStartupFailure()).hasMessageContaining("separate approval"));
        runner.withPropertyValues(settings).withPropertyValues("spring.profiles.active=prod",
                        "spring.flyway.enabled=true", "app.migrations.approved=true")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
