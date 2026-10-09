package com.motadev.clone_reddit.community.dtos.response;

import com.motadev.clone_reddit.media.dtos.response.MediaResponse;

import java.time.LocalDateTime;
import java.util.UUID;

public record CommunityResponseDTO(
        UUID communityId,
        String name,
        String slug,
        String description,
        Long topicId,
        String topicName,
        Long typeId,
        String typeName,
        MediaResponse icon,
        MediaResponse banner,
        Long memberCount,
        boolean isMember,
        LocalDateTime createdAt
    ) {
}
