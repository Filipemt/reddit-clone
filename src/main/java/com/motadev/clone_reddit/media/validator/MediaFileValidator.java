package com.motadev.clone_reddit.media.validator;

import com.motadev.clone_reddit.media.config.MediaUploadProperties;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@Component
public class MediaFileValidator {

    private static final int MAGIC_BYTES_HEADER_LENGTH = 12;

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

        validateMagicBytes(file, contentType);

        return extension;
    }

    private void validateMagicBytes(MultipartFile file, String contentType) {
        byte[] header;
        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(MAGIC_BYTES_HEADER_LENGTH);
        } catch (IOException ex) {
            throw new ResourceInvalidException("File content could not be read for type validation.");
        }

        if (!MediaMagicBytes.matches(contentType, header)) {
            throw new ResourceInvalidException(
                    "File content does not match the declared content type " + contentType + ".");
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }

        return originalFilename.substring(originalFilename.lastIndexOf('.'))
                .toLowerCase(Locale.ROOT);
    }
}
