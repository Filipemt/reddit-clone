package com.motadev.clone_reddit.post.service.impl;

import com.motadev.clone_reddit.auth.logging.AuthEventLog;
import com.motadev.clone_reddit.community.service.CommunityMembershipServiceI;
import com.motadev.clone_reddit.community.service.CommunityServiceI;
import com.motadev.clone_reddit.media.dtos.response.MediaResponse;
import com.motadev.clone_reddit.media.service.MediaServiceI;
import com.motadev.clone_reddit.messaging.outbox.service.OutboxServiceI;
import com.motadev.clone_reddit.post.converter.PostConverter;
import com.motadev.clone_reddit.post.dtos.request.CreatePostRequestDTO;
import com.motadev.clone_reddit.post.entity.Post;
import com.motadev.clone_reddit.post.logging.PostEventLog;
import com.motadev.clone_reddit.post.repository.PostRepository;
import com.motadev.clone_reddit.shared.exception.ForbiddenException;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UUID POST_ID = UUID.randomUUID();

    @Mock
    private PostRepository postRepository;
    @Mock
    private CommunityServiceI communityServiceI;
    @Mock
    private CommunityMembershipServiceI communityMembershipServiceI;
    @Mock
    private MediaServiceI mediaServiceI;
    @Mock
    private OutboxServiceI outboxServiceI;
    @Mock
    private MultipartFile mediaFile;

    private PostServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PostServiceImpl(
                postRepository,
                new PostConverter(),
                new AuthenticatedUserProvider(new AuthEventLog()),
                communityServiceI,
                communityMembershipServiceI,
                mediaServiceI,
                outboxServiceI,
                new PostEventLog()
        );
        setAuthenticatedUser(USER_ID, "SCOPE_BASIC");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPersistsPostEnqueuesOutboxAndUploadsMedia() {
        when(communityServiceI.requireActiveOwnerId(COMMUNITY_ID)).thenReturn(OWNER_ID);
        when(communityMembershipServiceI.isActiveMember(COMMUNITY_ID, USER_ID)).thenReturn(true);
        when(postRepository.saveAndFlush(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setPostId(POST_ID);
            return post;
        });
        when(mediaFile.isEmpty()).thenReturn(false);
        when(mediaServiceI.upload(eq(mediaFile), anyString()))
                .thenReturn(new MediaResponse(UUID.randomUUID(), "https://example.test/media"));

        var response = service.create(COMMUNITY_ID, new CreatePostRequestDTO("Hello", "body"), mediaFile);

        assertThat(response.postId()).isEqualTo(POST_ID);
        assertThat(response.title()).isEqualTo("Hello");
        assertThat(response.media()).isNotNull();
        verify(outboxServiceI).enqueue(
                eq("post"),
                eq(POST_ID),
                eq("notification.post.created"),
                eq("notification.post.created"),
                anyMap()
        );
    }

    @Test
    void createRejectsNonMember() {
        when(communityServiceI.requireActiveOwnerId(COMMUNITY_ID)).thenReturn(OWNER_ID);
        when(communityMembershipServiceI.isActiveMember(COMMUNITY_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(COMMUNITY_ID, new CreatePostRequestDTO("Hello", null), null))
                .isInstanceOf(ForbiddenException.class);

        verify(postRepository, never()).saveAndFlush(any());
        verify(outboxServiceI, never()).enqueue(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void deleteSoftDeletesWhenAuthor() {
        Post post = new Post();
        post.setPostId(POST_ID);
        post.setAuthorId(USER_ID);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        service.delete(POST_ID);

        assertThat(post.getDeletedAt()).isNotNull();
        assertThat(post.getDeletedBy()).isEqualTo(USER_ID);
    }

    @Test
    void deleteForbiddenForOtherUser() {
        Post post = new Post();
        post.setPostId(POST_ID);
        post.setAuthorId(UUID.randomUUID());
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.delete(POST_ID))
                .isInstanceOf(ForbiddenException.class);
        assertThat(post.getDeletedAt()).isNull();
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(postRepository.findByPostIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(POST_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static void setAuthenticatedUser(UUID userId, String... authorities) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId.toString(),
                "n/a",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
