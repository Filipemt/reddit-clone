package com.motadev.clone_reddit.community.dtos.response;

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
    UUID mediaId,
    UUID bannerMediaId,
    LocalDateTime createdAt
    ) {
}
