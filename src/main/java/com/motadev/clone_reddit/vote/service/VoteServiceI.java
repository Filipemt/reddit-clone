package com.motadev.clone_reddit.vote.service;

import com.motadev.clone_reddit.vote.dtos.request.VoteRequestDTO;
import com.motadev.clone_reddit.vote.dtos.response.VoteResponseDTO;

import java.util.UUID;

public interface VoteServiceI {

    VoteResponseDTO votePost(UUID postId, VoteRequestDTO dto);

    VoteResponseDTO voteComment(UUID commentId, VoteRequestDTO dto);
}
