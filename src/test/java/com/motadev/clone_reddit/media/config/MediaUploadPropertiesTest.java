package com.motadev.clone_reddit.media.config;

import com.motadev.clone_reddit.media.config.MediaTypeRule;
import com.motadev.clone_reddit.media.validator.MediaFileValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MediaUploadPropertiesTest {

    private MediaUploadProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));

        properties = new Binder(ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources))
                .bind("media.upload", Bindable.of(MediaUploadProperties.class))
                .orElseThrow(() -> new IllegalStateException(
                        "media.upload nao encontrado no application.yaml; o prefixo pode ter sido renomeado."));
    }

    @Test
    void deveVincularMaxSizeBytesEAsRegrasDoYamlReal() {
        assertThat(properties.maxSizeBytes()).isEqualTo(5_242_880L);

        assertThat(properties.types()).containsExactlyInAnyOrder(
                new MediaTypeRule("image/jpeg", Set.of(".jpg", ".jpeg")),
                new MediaTypeRule("image/png", Set.of(".png")),
                new MediaTypeRule("image/webp", Set.of(".webp")),
                new MediaTypeRule("image/gif", Set.of(".gif"))
        );
    }

    @Test
    void oValidadorConstruidoComOYamlRealDeveAceitarCadaFormatoDeclarado() {
        MediaFileValidator validator = new MediaFileValidator(properties);

        assertThat(validator.validateAndResolveExtension(file("icon.jpg", "image/jpeg"))).isEqualTo(".jpg");
        assertThat(validator.validateAndResolveExtension(file("icon.jpeg", "image/jpeg"))).isEqualTo(".jpeg");
        assertThat(validator.validateAndResolveExtension(file("icon.png", "image/png"))).isEqualTo(".png");
        assertThat(validator.validateAndResolveExtension(file("icon.webp", "image/webp"))).isEqualTo(".webp");
        assertThat(validator.validateAndResolveExtension(file("icon.gif", "image/gif"))).isEqualTo(".gif");
    }

    @Test
    void oValidadorConstruidoComOYamlRealDeveRejeitarExtensaoNaoDeclarada() {
        MediaFileValidator validator = new MediaFileValidator(properties);

        assertThat(properties.allowsContentType("image/bmp")).isFalse();
        assertThat(properties.allows("image/png", ".bmp")).isFalse();
    }

    private MockMultipartFile file(String originalFilename, String contentType) {
        return new MockMultipartFile("file", originalFilename, contentType, new byte[10]);
    }
}
