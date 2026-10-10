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
