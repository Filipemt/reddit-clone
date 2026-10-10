package com.motadev.clone_reddit.comment.dtos.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CommentResponseDTO(
        UUID commentId,
        UUID postId,
        UUID parentId,
        UUID authorId,
        String body,
        boolean deleted,
        long score,
        long upCount,
        long downCount,
        LocalDateTime createdAt,
        List<CommentResponseDTO> replies
) {
}
