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

    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00
    };
    private static final byte[] GIF_HEADER = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00};
    private static final byte[] WEBP_HEADER = {
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50
    };

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

        assertThat(validator.validateAndResolveExtension(file("icon.jpg", "image/jpeg", JPEG_HEADER))).isEqualTo(".jpg");
        assertThat(validator.validateAndResolveExtension(file("icon.jpeg", "image/jpeg", JPEG_HEADER))).isEqualTo(".jpeg");
        assertThat(validator.validateAndResolveExtension(file("icon.png", "image/png", PNG_HEADER))).isEqualTo(".png");
        assertThat(validator.validateAndResolveExtension(file("icon.webp", "image/webp", WEBP_HEADER))).isEqualTo(".webp");
        assertThat(validator.validateAndResolveExtension(file("icon.gif", "image/gif", GIF_HEADER))).isEqualTo(".gif");
    }

    @Test
    void oValidadorConstruidoComOYamlRealDeveRejeitarExtensaoNaoDeclarada() {
        MediaFileValidator validator = new MediaFileValidator(properties);

        assertThat(properties.allowsContentType("image/bmp")).isFalse();
        assertThat(properties.allows("image/png", ".bmp")).isFalse();
    }

    private MockMultipartFile file(String originalFilename, String contentType, byte[] content) {
        return new MockMultipartFile("file", originalFilename, contentType, content);
    }
}
