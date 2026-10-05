package com.motadev.clone_reddit.media.validator;

import com.motadev.clone_reddit.media.config.MediaUploadProperties;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@Component
public class MediaFileValidator {

    private final MediaUploadProperties properties;

    public MediaFileValidator(MediaUploadProperties properties) {
        this.properties = properties;
    }

    public String validateAndResolveExtension(MultipartFile file) {
        if (file.getSize() > properties.maxSizeBytes()) {
            throw new ResourceInvalidException(
                    "File exceeds the maximum allowed size of " + properties.maxSizeBytes() + " bytes.");
        }

        String contentType = file.getContentType();
        String extension = extractExtension(file.getOriginalFilename());

        if (!properties.allowsContentType(contentType)) {
            throw new ResourceInvalidException("File content type is not allowed.");
        }

        if (!properties.allows(contentType, extension)) {
            throw new ResourceInvalidException(
                    "File extension is not allowed for content type " + contentType + ".");
        }

        return extension;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }

        return originalFilename.substring(originalFilename.lastIndexOf('.'))
                .toLowerCase(Locale.ROOT);
    }
}
