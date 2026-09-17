package com.motadev.clone_reddit.user.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRequestDTO(
        @NotBlank
        String username,

        @Size(min = 8, max = 72)
        @NotBlank
        String password) {
}
