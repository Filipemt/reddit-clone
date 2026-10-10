package com.motadev.clone_reddit.vote.dtos.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record VoteRequestDTO(
        @NotNull
        @Min(-1)
        @Max(1)
        Integer value
) {
}
