package com.motadev.clone_reddit.user.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRequestDTO(
        @NotBlank(message = "Username is mandatory.")
        @Size(min = 3, max = 20)
        String username,

        @Size(min = 8, max = 72)
        @NotBlank(message = "Message is mandatory.")
        String password,

        @NotBlank
        @Email(message = "E-mail is mandatory.")
        String email
        ) {}
