package com.padle.core.padelcoreservice.preview;

import com.padle.core.padelcoreservice.config.SchedulingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfiguration.class);

    @Test
    void schedulingRemainsEnabledByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void previewRegistersNoScheduledProcessor() {
        runner.withPropertyValues("app.scheduling.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
