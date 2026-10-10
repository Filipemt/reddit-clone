package com.motadev.clone_reddit.comment.logging;

import com.motadev.clone_reddit.comment.service.impl.CommentServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CommentEventLog {

    private final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);

    public void createSuccess(UUID commentId, UUID postId, UUID authorId, UUID parentId) {
        log.atInfo()
                .addKeyValue("event", "comment.create.success")
                .addKeyValue("commentId", commentId)
                .addKeyValue("postId", postId)
                .addKeyValue("authorId", authorId)
                .addKeyValue("parentId", parentId)
                .setMessage("Comment created")
                .log();
    }

    public void createParentMismatch(UUID postId, UUID parentId) {
        log.atWarn()
                .addKeyValue("event", "comment.create.parent_mismatch")
                .addKeyValue("postId", postId)
                .addKeyValue("parentId", parentId)
                .setMessage("Parent comment does not belong to the post")
                .log();
    }

    public void deleteSuccess(UUID commentId, UUID userId, boolean isAuthor) {
        log.atInfo()
                .addKeyValue("event", "comment.delete.success")
                .addKeyValue("commentId", commentId)
                .addKeyValue("userId", userId)
                .addKeyValue("isAuthor", isAuthor)
                .setMessage("Comment soft deleted")
                .log();
    }

    public void deleteAlreadyDeleted(UUID commentId, UUID userId) {
        log.atDebug()
                .addKeyValue("event", "comment.delete.already_deleted")
                .addKeyValue("commentId", commentId)
                .addKeyValue("userId", userId)
                .setMessage("Comment was already soft deleted")
                .log();
    }

    public void deleteForbidden(UUID commentId, UUID userId) {
        log.atWarn()
                .addKeyValue("event", "comment.delete.forbidden")
                .addKeyValue("commentId", commentId)
                .addKeyValue("userId", userId)
                .setMessage("User is not allowed to delete this comment")
                .log();
    }
}
