package com.motadev.clone_reddit.vote.dtos.response;

import com.motadev.clone_reddit.vote.entity.VoteTargetType;

import java.util.UUID;

public record VoteResponseDTO(
        VoteTargetType targetType,
        UUID targetId,
        int value,
        long score
) {
}
