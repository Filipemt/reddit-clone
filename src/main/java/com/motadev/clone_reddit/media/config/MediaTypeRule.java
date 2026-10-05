package com.motadev.clone_reddit.media.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record MediaTypeRule(

        @NotBlank(message = "media.upload.types[].content-type is required.")
        String contentType,

        @NotEmpty(message = "media.upload.types[].extensions must declare at least one extension.")
        Set<String> extensions
) {

    public boolean acceptsContentType(String contentType) {
        return this.contentType.equals(contentType);
    }

    public boolean allows(String contentType, String extension) {
        return acceptsContentType(contentType) && extensions.contains(extension);
    }
}
