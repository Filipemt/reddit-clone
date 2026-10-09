package com.motadev.clone_reddit.post.converter;

import com.motadev.clone_reddit.media.dtos.response.MediaResponse;
import com.motadev.clone_reddit.post.dtos.request.CreatePostRequestDTO;
import com.motadev.clone_reddit.post.dtos.response.PostResponseDTO;
import com.motadev.clone_reddit.post.entity.Post;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class PostConverter {

    public Post toEntity(CreatePostRequestDTO dto, UUID communityId, UUID authorId, UUID mediaId) {
        var post = new Post();
        post.setCommunityId(communityId);
        post.setAuthorId(authorId);
        post.setTitle(dto.title());
        post.setBody(blankToNull(dto.body()));
        post.setMediaId(mediaId);
        return post;
    }

    public PostResponseDTO toResponseDto(Post post, Map<UUID, String> mediaUrls) {
        MediaResponse media = null;
        if (post.getMediaId() != null) {
            media = new MediaResponse(post.getMediaId(), mediaUrls.get(post.getMediaId()));
        }

        return new PostResponseDTO(
                post.getPostId(),
                post.getCommunityId(),
                post.getAuthorId(),
                post.getTitle(),
                post.getBody(),
                media,
                post.getScore(),
                post.getUpCount(),
                post.getDownCount(),
                post.getCommentCount(),
                post.getHotScore(),
                post.getCreatedAt()
        );
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
