package com.motadev.clone_reddit.auth.dtos.request;

import jakarta.validation.constraints.NotBlank;

public record RevokeRequest(
        @NotBlank
        String tokenValue) {
}