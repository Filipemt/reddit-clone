package com.motadev.clone_reddit.user.dtos.response;

import java.util.Set;
import java.util.UUID;

public record UserAuthInfo(UUID userId, Set<String> roles) {
}
