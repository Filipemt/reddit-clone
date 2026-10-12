package com.motadev.clone_reddit.post.service.impl;

import com.motadev.clone_reddit.community.service.CommunityMembershipServiceI;
import com.motadev.clone_reddit.community.service.CommunityServiceI;
import com.motadev.clone_reddit.media.dtos.response.MediaResponse;
import com.motadev.clone_reddit.media.service.MediaServiceI;
import com.motadev.clone_reddit.messaging.outbox.service.OutboxServiceI;
import com.motadev.clone_reddit.post.converter.PostConverter;
import com.motadev.clone_reddit.post.dtos.request.CreatePostRequestDTO;
import com.motadev.clone_reddit.post.dtos.response.PostResponseDTO;
import com.motadev.clone_reddit.post.entity.Post;
import com.motadev.clone_reddit.post.logging.PostEventLog;
import com.motadev.clone_reddit.post.repository.PostRepository;
import com.motadev.clone_reddit.post.service.PostServiceI;
import com.motadev.clone_reddit.post.sort.HotScoreCalculator;
import com.motadev.clone_reddit.post.sort.PostSort;
import com.motadev.clone_reddit.post.sort.TopPeriod;
import com.motadev.clone_reddit.shared.dtos.response.PagedResponseDTO;
import com.motadev.clone_reddit.shared.exception.ForbiddenException;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import com.motadev.clone_reddit.shared.web.Pageables;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PostServiceImpl implements PostServiceI {

    private final PostRepository postRepository;
    private final PostConverter postConverter;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final CommunityServiceI communityServiceI;
    private final CommunityMembershipServiceI communityMembershipServiceI;
    private final MediaServiceI mediaServiceI;
    private final OutboxServiceI outboxServiceI;
    private final PostEventLog postEventLog;

    public PostServiceImpl(
            PostRepository postRepository,
            PostConverter postConverter,
            AuthenticatedUserProvider authenticatedUserProvider,
            CommunityServiceI communityServiceI,
            CommunityMembershipServiceI communityMembershipServiceI,
            MediaServiceI mediaServiceI,
            OutboxServiceI outboxServiceI,
            PostEventLog postEventLog
    ) {
        this.postRepository = postRepository;
        this.postConverter = postConverter;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.communityServiceI = communityServiceI;
        this.communityMembershipServiceI = communityMembershipServiceI;
        this.mediaServiceI = mediaServiceI;
        this.outboxServiceI = outboxServiceI;
        this.postEventLog = postEventLog;
    }

    @Override
    @Transactional
    public PostResponseDTO create(UUID communityId, CreatePostRequestDTO dto, MultipartFile mediaFile) {
        UUID authorId = authenticatedUserProvider.extractUserIdFromAuthentication();
        UUID ownerId = communityServiceI.requireActiveOwnerId(communityId);

        if (!communityMembershipServiceI.isActiveMember(communityId, authorId)) {
            postEventLog.createForbidden(communityId, authorId);
            throw new ForbiddenException("You are not allowed to post in this community.");
        }

        Post post = postConverter.toEntity(dto, communityId, authorId, null);
        Post saved = postRepository.saveAndFlush(post);
        refreshHotScore(saved);

        MediaResponse media = uploadIfPresent(mediaFile, mediaFolderOf(saved.getPostId()));
        if (media != null) {
            saved.setMediaId(media.mediaId());
        }

        enqueuePostCreated(saved, ownerId);
        postEventLog.createSuccess(saved.getPostId(), communityId, authorId, media != null);

        return postConverter.toResponseDto(saved, urlsOf(media));
    }

    @Override
    @Transactional
    public PostResponseDTO getById(UUID postId) {
        Post post = findActiveOrThrow(postId);
        postEventLog.getSuccess(postId);
        return postConverter.toResponseDto(post, urlsFor(post.getMediaId()));
    }

    @Override
    @Transactional
    public PagedResponseDTO<PostResponseDTO> listByCommunity(
            UUID communityId,
            Pageable pageable,
            String sort,
            String period
    ) {
        communityServiceI.requireActiveOwnerId(communityId);

        PostSort postSort;
        TopPeriod topPeriod;
        try {
            postSort = PostSort.from(sort);
            topPeriod = TopPeriod.from(period);
        } catch (IllegalArgumentException ex) {
            throw new ResourceInvalidException("Invalid sort or period.");
        }

        LocalDateTime cutoff = postSort == PostSort.TOP ? topPeriod.cutoff(LocalDateTime.now()) : null;
        Page<Post> page = postRepository.findActiveByCommunityAndCreatedAtAfter(
                communityId,
                cutoff,
                withSort(pageable, postSort)
        );

        Map<UUID, String> urls = mediaServiceI.getUrls(
                page.getContent().stream()
                        .map(Post::getMediaId)
                        .filter(Objects::nonNull)
                        .toList()
        );

        return PagedResponseDTO.from(page, post -> postConverter.toResponseDto(post, urls));
    }

    @Override
    @Transactional
    public UUID requireActiveAuthorId(UUID postId) {
        return findActiveOrThrow(postId).getAuthorId();
    }

    @Override
    @Transactional
    public void adjustCommentCount(UUID postId, long delta) {
        int updated = postRepository.adjustCommentCount(postId, delta);
        if (updated == 0) {
            throw new ResourceNotFoundException("Post not found.");
        }
    }

    @Override
    @Transactional
    public UUID applyVoteDelta(UUID postId, long scoreDelta, long upDelta, long downDelta) {
        Post post = findActiveOrThrow(postId);
        int updated = postRepository.applyVoteDelta(postId, scoreDelta, upDelta, downDelta);
        if (updated == 0) {
            throw new ResourceNotFoundException("Post not found.");
        }
        post.setScore(post.getScore() + scoreDelta);
        refreshHotScore(post);
        return post.getAuthorId();
    }

    @Override
    @Transactional
    public void delete(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found."));
        UUID userId = authenticatedUserProvider.extractUserIdFromAuthentication();

        boolean isAuthor = post.getAuthorId().equals(userId);
        boolean isAdmin = authenticatedUserProvider.hasRole(RoleValues.ADMIN);
        if (!isAuthor && !isAdmin) {
            postEventLog.deleteForbidden(postId, userId);
            throw new ForbiddenException("You are not allowed to delete this post.");
        }

        if (post.getDeletedAt() != null) {
            postEventLog.deleteAlreadyDeleted(postId, userId);
            return;
        }

        post.setDeletedAt(LocalDateTime.now());
        post.setDeletedBy(userId);
        postEventLog.deleteSuccess(postId, userId, isAuthor);
    }

    private void enqueuePostCreated(Post post, UUID ownerId) {
        outboxServiceI.enqueue(
                "post",
                post.getPostId(),
                "notification.post.created",
                "notification.post.created",
                Map.of(
                        "postId", post.getPostId().toString(),
                        "communityId", post.getCommunityId().toString(),
                        "authorId", post.getAuthorId().toString(),
                        "ownerId", ownerId.toString(),
                        "title", post.getTitle()
                )
        );
    }

    private Post findActiveOrThrow(UUID postId) {
        return postRepository.findByPostIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found."));
    }

    private MediaResponse uploadIfPresent(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        return mediaServiceI.upload(file, folder);
    }

    private static String mediaFolderOf(UUID postId) {
        return "posts/" + postId;
    }

    private Map<UUID, String> urlsOf(MediaResponse media) {
        if (media == null) {
            return Map.of();
        }
        return Map.of(media.mediaId(), media.url());
    }

    private Map<UUID, String> urlsFor(UUID mediaId) {
        if (mediaId == null) {
            return Map.of();
        }
        return mediaServiceI.getUrls(List.of(mediaId));
    }

    private void refreshHotScore(Post post) {
        LocalDateTime createdAt = post.getCreatedAt() == null ? LocalDateTime.now() : post.getCreatedAt();
        double hotScore = HotScoreCalculator.compute(post.getScore(), createdAt);
        post.setHotScore(hotScore);
        postRepository.updateHotScore(post.getPostId(), hotScore);
    }

    private static Pageable withSort(Pageable pageable, PostSort sort) {
        Sort order = switch (sort) {
            case NEW -> Pageables.NEWEST_BY_CREATED_AT;
            case HOT -> Sort.by(Sort.Direction.DESC, "hotScore").and(Pageables.NEWEST_BY_CREATED_AT);
            case TOP -> Sort.by(Sort.Direction.DESC, "score").and(Pageables.NEWEST_BY_CREATED_AT);
        };
        return Pageables.withSort(pageable, order);
    }
}
