package com.motadev.clone_reddit.user.dtos.response;

import java.util.UUID;

public record UserResponseDTO(UUID userId, String username, String email, Integer karma) {
}
