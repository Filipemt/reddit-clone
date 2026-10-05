package com.motadev.clone_reddit.media.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "media.upload")
public record MediaUploadProperties(

        @Min(value = 1, message = "media.upload.max-size-bytes must be greater than zero.")
        long maxSizeBytes,

        @NotEmpty(message = "media.upload.types must declare at least one rule.")
        @Valid
        List<MediaTypeRule> types
) {

    public boolean allowsContentType(String contentType) {
        return types.stream().anyMatch(rule -> rule.acceptsContentType(contentType));
    }

    public boolean allows(String contentType, String extension) {
        return types.stream().anyMatch(rule -> rule.allows(contentType, extension));
    }
}
