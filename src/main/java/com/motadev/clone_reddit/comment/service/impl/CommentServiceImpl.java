package com.motadev.clone_reddit.comment.service.impl;

import com.motadev.clone_reddit.comment.converter.CommentConverter;
import com.motadev.clone_reddit.comment.dtos.request.CreateCommentRequestDTO;
import com.motadev.clone_reddit.comment.dtos.response.CommentResponseDTO;
import com.motadev.clone_reddit.comment.entity.Comment;
import com.motadev.clone_reddit.comment.logging.CommentEventLog;
import com.motadev.clone_reddit.comment.repository.CommentRepository;
import com.motadev.clone_reddit.comment.service.CommentServiceI;
import com.motadev.clone_reddit.messaging.outbox.service.OutboxServiceI;
import com.motadev.clone_reddit.post.service.PostServiceI;
import com.motadev.clone_reddit.shared.exception.ForbiddenException;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CommentServiceImpl implements CommentServiceI {

    private final CommentRepository commentRepository;
    private final CommentConverter commentConverter;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final PostServiceI postServiceI;
    private final OutboxServiceI outboxServiceI;
    private final CommentEventLog commentEventLog;

    public CommentServiceImpl(
            CommentRepository commentRepository,
            CommentConverter commentConverter,
            AuthenticatedUserProvider authenticatedUserProvider,
            PostServiceI postServiceI,
            OutboxServiceI outboxServiceI,
            CommentEventLog commentEventLog
    ) {
        this.commentRepository = commentRepository;
        this.commentConverter = commentConverter;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.postServiceI = postServiceI;
        this.outboxServiceI = outboxServiceI;
        this.commentEventLog = commentEventLog;
    }

    @Override
    @Transactional
    public CommentResponseDTO create(UUID postId, CreateCommentRequestDTO dto) {
        UUID authorId = authenticatedUserProvider.extractUserIdFromAuthentication();
        UUID postAuthorId = postServiceI.requireActiveAuthorId(postId);

        UUID parentId = dto.parentId();
        UUID notifyUserId = postAuthorId;
        String eventType = "notification.post.commented";

        if (parentId != null) {
            Comment parent = commentRepository.findByCommentIdAndDeletedAtIsNull(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Comment not found."));
            if (!parent.getPostId().equals(postId)) {
                commentEventLog.createParentMismatch(postId, parentId);
                throw new ResourceInvalidException("Parent comment does not belong to this post.");
            }
            notifyUserId = parent.getAuthorId();
            eventType = "notification.comment.replied";
        }

        Comment saved = commentRepository.save(commentConverter.toEntity(dto, postId, authorId, parentId));
        postServiceI.adjustCommentCount(postId, 1);

        if (!notifyUserId.equals(authorId)) {
            outboxServiceI.enqueue(
                    "comment",
                    saved.getCommentId(),
                    eventType,
                    eventType,
                    Map.of(
                            "commentId", saved.getCommentId().toString(),
                            "postId", postId.toString(),
                            "authorId", authorId.toString(),
                            "recipientId", notifyUserId.toString(),
                            "parentId", parentId == null ? "" : parentId.toString()
                    )
            );
        }

        commentEventLog.createSuccess(saved.getCommentId(), postId, authorId, parentId);
        return commentConverter.toResponseDto(saved);
    }

    @Override
    @Transactional
    public List<CommentResponseDTO> listTreeByPost(UUID postId) {
        postServiceI.requireActiveAuthorId(postId);
        List<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId);
        return buildTree(comments);
    }

    @Override
    @Transactional
    public UUID applyVoteDelta(UUID commentId, long scoreDelta, long upDelta, long downDelta) {
        Comment comment = commentRepository.findByCommentIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found."));
        int updated = commentRepository.applyVoteDelta(commentId, scoreDelta, upDelta, downDelta);
        if (updated == 0) {
            throw new ResourceNotFoundException("Comment not found.");
        }
        return comment.getAuthorId();
    }

    @Override
    @Transactional
    public long requireActiveScore(UUID commentId) {
        return commentRepository.findByCommentIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found."))
                .getScore();
    }

    @Override
    @Transactional
    public void delete(UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found."));
        UUID userId = authenticatedUserProvider.extractUserIdFromAuthentication();

        boolean isAuthor = comment.getAuthorId().equals(userId);
        boolean isAdmin = authenticatedUserProvider.hasRole(RoleValues.ADMIN);
        if (!isAuthor && !isAdmin) {
            commentEventLog.deleteForbidden(commentId, userId);
            throw new ForbiddenException("You are not allowed to delete this comment.");
        }

        if (comment.getDeletedAt() != null) {
            commentEventLog.deleteAlreadyDeleted(commentId, userId);
            return;
        }

        comment.setDeletedAt(LocalDateTime.now());
        comment.setDeletedBy(userId);
        commentEventLog.deleteSuccess(commentId, userId, isAuthor);
    }

    private List<CommentResponseDTO> buildTree(List<Comment> comments) {
        Map<UUID, List<Comment>> childrenByParent = new HashMap<>();
        List<Comment> roots = new ArrayList<>();

        for (Comment comment : comments) {
            if (comment.getParentId() == null) {
                roots.add(comment);
            } else {
                childrenByParent.computeIfAbsent(comment.getParentId(), ignored -> new ArrayList<>()).add(comment);
            }
        }

        return roots.stream().map(root -> toNode(root, childrenByParent)).toList();
    }

    private CommentResponseDTO toNode(Comment comment, Map<UUID, List<Comment>> childrenByParent) {
        List<Comment> children = childrenByParent.getOrDefault(comment.getCommentId(), List.of());
        List<CommentResponseDTO> replies = children.stream()
                .map(child -> toNode(child, childrenByParent))
                .toList();
        return commentConverter.toResponseDto(comment, replies);
    }
}
