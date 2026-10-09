package com.motadev.clone_reddit.comment.controller;

import com.motadev.clone_reddit.comment.dtos.request.CreateCommentRequestDTO;
import com.motadev.clone_reddit.comment.dtos.response.CommentResponseDTO;
import com.motadev.clone_reddit.comment.service.CommentServiceI;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class CommentController {

    private final CommentServiceI commentServiceI;

    public CommentController(CommentServiceI commentServiceI) {
        this.commentServiceI = commentServiceI;
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponseDTO> create(
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequestDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentServiceI.create(postId, dto));
    }

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<List<CommentResponseDTO>> listTree(@PathVariable UUID postId) {
        return ResponseEntity.ok(commentServiceI.listTreeByPost(postId));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID commentId) {
        commentServiceI.delete(commentId);
        return ResponseEntity.noContent().build();
    }
}
