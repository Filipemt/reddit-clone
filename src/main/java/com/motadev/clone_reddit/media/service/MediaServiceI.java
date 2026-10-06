package com.motadev.clone_reddit.media.service;

import com.motadev.clone_reddit.media.dtos.response.MediaResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface MediaServiceI {

    MediaResponse upload(MultipartFile file, String folder);
    String getUrl(UUID mediaId);

    Map<UUID, String> getUrls(Collection<UUID> mediaIds);

    void deleteAfterCommit(Collection<UUID> mediaIds);
}
