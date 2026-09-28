package com.motadev.clone_reddit.media.service;

import com.motadev.clone_reddit.media.dtos.request.MediaUploadRequest;
import com.motadev.clone_reddit.media.dtos.response.MediaResponse;

import java.util.UUID;

public interface MediaServiceI {

    MediaResponse upload(MediaUploadRequest mediaUploadRequest);
    String getUrl(UUID mediaId);
}
