package com.motadev.clone_reddit.vote.controller;

import com.motadev.clone_reddit.vote.dtos.request.VoteRequestDTO;
import com.motadev.clone_reddit.vote.dtos.response.VoteResponseDTO;
import com.motadev.clone_reddit.vote.service.VoteServiceI;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping
public class VoteController {

    private final VoteServiceI voteServiceI;

    public VoteController(VoteServiceI voteServiceI) {
        this.voteServiceI = voteServiceI;
    }

    @PutMapping("/posts/{postId}/vote")
    public ResponseEntity<VoteResponseDTO> votePost(
            @PathVariable UUID postId,
            @Valid @RequestBody VoteRequestDTO dto
    ) {
        return ResponseEntity.ok(voteServiceI.votePost(postId, dto));
    }

    @PutMapping("/comments/{commentId}/vote")
    public ResponseEntity<VoteResponseDTO> voteComment(
            @PathVariable UUID commentId,
            @Valid @RequestBody VoteRequestDTO dto
    ) {
        return ResponseEntity.ok(voteServiceI.voteComment(commentId, dto));
    }
}
