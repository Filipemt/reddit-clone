package com.motadev.clone_reddit.auth.dtos.response;

public record TokenData(String accessToken, Long expiresIn) {
}
