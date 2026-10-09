package com.motadev.clone_reddit.comment.service;

import com.motadev.clone_reddit.comment.dtos.request.CreateCommentRequestDTO;
import com.motadev.clone_reddit.comment.dtos.response.CommentResponseDTO;

import java.util.List;
import java.util.UUID;

public interface CommentServiceI {

    CommentResponseDTO create(UUID postId, CreateCommentRequestDTO dto);

    List<CommentResponseDTO> listTreeByPost(UUID postId);

    void delete(UUID commentId);
}
