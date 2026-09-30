package com.motadev.clone_reddit.media.dtos.request;

public record MediaUploadRequest(byte[] content,
                                 String contentType,
                                 String folder) {
}
