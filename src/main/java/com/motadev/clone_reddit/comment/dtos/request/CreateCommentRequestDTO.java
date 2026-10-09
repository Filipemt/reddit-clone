package com.motadev.clone_reddit.comment.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCommentRequestDTO(
        @NotBlank
        @Size(max = 10000)
        String body,

        UUID parentId
) {
}
