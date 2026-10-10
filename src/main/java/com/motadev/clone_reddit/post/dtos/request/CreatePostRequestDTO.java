package com.motadev.clone_reddit.post.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostRequestDTO(
        @NotBlank
        @Size(max = 300)
        String title,

        @Size(max = 40000)
        String body
) {
}
