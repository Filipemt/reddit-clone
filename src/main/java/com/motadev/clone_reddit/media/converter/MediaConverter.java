package com.motadev.clone_reddit.media.converter;

import com.motadev.clone_reddit.media.dtos.request.MediaUploadRequest;
import com.motadev.clone_reddit.media.dtos.response.MediaResponse;
import com.motadev.clone_reddit.media.entity.Media;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

@Component
public class MediaConverter {

    public MediaUploadRequest toUploadRequest(MultipartFile file, String folder) {
        try {
            return new MediaUploadRequest(file.getBytes(), file.getContentType(), folder);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }

    public Media toEntity(MediaUploadRequest request, String bucketName, String objectKey) {
        Media media = new Media();
        media.setBucket(bucketName);
        media.setObjectKey(objectKey);
        media.setContentType(request.contentType());
        media.setSizeBytes((long) request.content().length);
        return media;
    }

    public MediaResponse toResponse(Media media, String url) {
        return new MediaResponse(media.getMediaId(), url);
    }
}
