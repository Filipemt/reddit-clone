package com.motadev.clone_reddit.media.config;

import com.motadev.clone_reddit.media.validator.MediaFileValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class MediaUploadPropertiesScanTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ScanTestConfig.class, MediaFileValidator.class);

    @Configuration(proxyBeanMethods = false)
    @ConfigurationPropertiesScan("com.motadev.clone_reddit.media.config")
    static class ScanTestConfig {
    }

    @Test
    void oScanDeveDescobrirEVincularAConfiguracaoDoApplicationYaml() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();

            MediaUploadProperties properties = context.getBean(MediaUploadProperties.class);
            assertThat(properties.maxSizeBytes()).isEqualTo(5_242_880L);
            assertThat(properties.types()).hasSize(4);
            assertThat(properties.allows("image/png", ".png")).isTrue();
            assertThat(properties.allows("image/png", ".jpg")).isFalse();
        });
    }

    @Test
    void oValidadorDeveReceberAConfiguracaoAutomaticamentePorInjecao() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MediaFileValidator.class)).isNotNull();
        });
    }

    @Test
    void deveFalharNoBootQuandoMaxSizeBytesEZero() {
        runner.withPropertyValues("media.upload.max-size-bytes=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("media.upload.max-size-bytes must be greater than zero");
                });
    }

    @Test
    void deveFalharNoBootQuandoNenhumaRegraEDeclarada() {
        runner.withPropertyValues("media.upload.types=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("media.upload.types must declare at least one rule");
                });
    }
}
