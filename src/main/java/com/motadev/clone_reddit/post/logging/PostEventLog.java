package com.motadev.clone_reddit.post.logging;

import com.motadev.clone_reddit.post.service.impl.PostServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PostEventLog {

    private final Logger log = LoggerFactory.getLogger(PostServiceImpl.class);

    public void createSuccess(UUID postId, UUID communityId, UUID authorId, boolean hasMedia) {
        log.atInfo()
                .addKeyValue("event", "post.create.success")
                .addKeyValue("postId", postId)
                .addKeyValue("communityId", communityId)
                .addKeyValue("authorId", authorId)
                .addKeyValue("hasMedia", hasMedia)
                .setMessage("Post created")
                .log();
    }

    public void createForbidden(UUID communityId, UUID userId) {
        log.atWarn()
                .addKeyValue("event", "post.create.forbidden")
                .addKeyValue("communityId", communityId)
                .addKeyValue("userId", userId)
                .setMessage("User is not a member of the community")
                .log();
    }

    public void deleteSuccess(UUID postId, UUID userId, boolean isAuthor) {
        log.atInfo()
                .addKeyValue("event", "post.delete.success")
                .addKeyValue("postId", postId)
                .addKeyValue("userId", userId)
                .addKeyValue("isAuthor", isAuthor)
                .setMessage("Post soft deleted")
                .log();
    }

    public void deleteAlreadyDeleted(UUID postId, UUID userId) {
        log.atDebug()
                .addKeyValue("event", "post.delete.already_deleted")
                .addKeyValue("postId", postId)
                .addKeyValue("userId", userId)
                .setMessage("Post was already soft deleted")
                .log();
    }

    public void deleteForbidden(UUID postId, UUID userId) {
        log.atWarn()
                .addKeyValue("event", "post.delete.forbidden")
                .addKeyValue("postId", postId)
                .addKeyValue("userId", userId)
                .setMessage("User is not allowed to delete this post")
                .log();
    }

    public void getSuccess(UUID postId) {
        log.atDebug()
                .addKeyValue("event", "post.get.success")
                .addKeyValue("postId", postId)
                .setMessage("Post fetched")
                .log();
    }
}
