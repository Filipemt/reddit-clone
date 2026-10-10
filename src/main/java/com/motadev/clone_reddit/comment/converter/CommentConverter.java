package com.motadev.clone_reddit.comment.converter;

import com.motadev.clone_reddit.comment.dtos.request.CreateCommentRequestDTO;
import com.motadev.clone_reddit.comment.dtos.response.CommentResponseDTO;
import com.motadev.clone_reddit.comment.entity.Comment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class CommentConverter {

    public Comment toEntity(CreateCommentRequestDTO dto, UUID postId, UUID authorId, UUID parentId) {
        var comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(authorId);
        comment.setParentId(parentId);
        comment.setBody(dto.body());
        return comment;
    }

    public CommentResponseDTO toResponseDto(Comment comment, List<CommentResponseDTO> replies) {
        boolean deleted = comment.getDeletedAt() != null;
        return new CommentResponseDTO(
                comment.getCommentId(),
                comment.getPostId(),
                comment.getParentId(),
                deleted ? null : comment.getAuthorId(),
                deleted ? null : comment.getBody(),
                deleted,
                comment.getScore(),
                comment.getUpCount(),
                comment.getDownCount(),
                comment.getCreatedAt(),
                replies == null ? List.of() : replies
        );
    }

    public CommentResponseDTO toResponseDto(Comment comment) {
        return toResponseDto(comment, new ArrayList<>());
    }
}
