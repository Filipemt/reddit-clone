package com.motadev.clone_reddit.post.dtos.response;

import com.motadev.clone_reddit.media.dtos.response.MediaResponse;

import java.time.LocalDateTime;
import java.util.UUID;

public record PostResponseDTO(
        UUID postId,
        UUID communityId,
        UUID authorId,
        String title,
        String body,
        MediaResponse media,
        long score,
        long upCount,
        long downCount,
        long commentCount,
        double hotScore,
        LocalDateTime createdAt
) {
}
