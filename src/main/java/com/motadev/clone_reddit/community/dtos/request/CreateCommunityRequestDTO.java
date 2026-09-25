package com.motadev.clone_reddit.community.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCommunityRequestDTO(

        @NotBlank(message = "Community name is required.")
        @Size(min = 3, max = 100, message = "Community name must be between 3 and 100 characters.")
        String name,

        @NotBlank(message = "Slug is required.")
        @Size(min = 3, max = 100, message = "Slug must be between 3 and 100 characters.")
        String slug,

        @NotBlank(message = "Description is required.")
        @Size(max = 255, message = "Description cannot exceed 255 characters.")
        String description,

        @NotNull(message = "Topic ID is required.")
        Long topicId,

        @NotNull(message = "Type ID is required.")
        Long typeId,

        @NotNull(message = "Status ID is required.")
        Long statusId,

        UUID mediaId,
        UUID bannerMediaId

) {
}
