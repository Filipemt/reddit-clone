package com.motadev.clone_reddit.vote.service.impl;

import com.motadev.clone_reddit.auth.logging.AuthEventLog;
import com.motadev.clone_reddit.comment.service.CommentServiceI;
import com.motadev.clone_reddit.messaging.outbox.service.OutboxServiceI;
import com.motadev.clone_reddit.post.dtos.response.PostResponseDTO;
import com.motadev.clone_reddit.post.service.PostServiceI;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import com.motadev.clone_reddit.user.service.UserServiceI;
import com.motadev.clone_reddit.vote.dtos.request.VoteRequestDTO;
import com.motadev.clone_reddit.vote.entity.Vote;
import com.motadev.clone_reddit.vote.entity.VoteTargetType;
import com.motadev.clone_reddit.vote.logging.VoteEventLog;
import com.motadev.clone_reddit.vote.repository.VoteRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final UUID POST_ID = UUID.randomUUID();

    @Mock
    private VoteRepository voteRepository;
    @Mock
    private PostServiceI postServiceI;
    @Mock
    private CommentServiceI commentServiceI;
    @Mock
    private UserServiceI userServiceI;
    @Mock
    private OutboxServiceI outboxServiceI;

    private VoteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VoteServiceImpl(
                voteRepository,
                new AuthenticatedUserProvider(new AuthEventLog()),
                postServiceI,
                commentServiceI,
                userServiceI,
                outboxServiceI,
                new VoteEventLog()
        );
        setAuthenticatedUser(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void upvoteCreatesVoteUpdatesScoreAndKarma() {
        when(voteRepository.findByUserIdAndTargetTypeAndTargetId(USER_ID, VoteTargetType.POST, POST_ID))
                .thenReturn(Optional.empty());
        when(postServiceI.getById(POST_ID)).thenReturn(postResponse(10));
        when(postServiceI.applyVoteDelta(POST_ID, 1, 1, 0)).thenReturn(AUTHOR_ID);

        var response = service.votePost(POST_ID, new VoteRequestDTO(1));

        assertThat(response.value()).isEqualTo(1);
        assertThat(response.score()).isEqualTo(11);
        verify(userServiceI).adjustKarma(AUTHOR_ID, 1);
        verify(outboxServiceI).enqueue(eq("vote"), eq(POST_ID), eq("notification.vote.upvoted"), eq("notification.vote.upvoted"), anyMap());
        ArgumentCaptor<Vote> voteCaptor = ArgumentCaptor.forClass(Vote.class);
        verify(voteRepository).save(voteCaptor.capture());
        assertThat(voteCaptor.getValue().getValue()).isEqualTo((short) 1);
    }

    @Test
    void switchingUpvoteToDownvoteAppliesDoubleScoreDelta() {
        Vote existing = new Vote();
        existing.setUserId(USER_ID);
        existing.setTargetType(VoteTargetType.POST);
        existing.setTargetId(POST_ID);
        existing.setValue((short) 1);

        when(voteRepository.findByUserIdAndTargetTypeAndTargetId(USER_ID, VoteTargetType.POST, POST_ID))
                .thenReturn(Optional.of(existing));
        when(postServiceI.getById(POST_ID)).thenReturn(postResponse(5));
        when(postServiceI.applyVoteDelta(POST_ID, -2, -1, 1)).thenReturn(AUTHOR_ID);

        var response = service.votePost(POST_ID, new VoteRequestDTO(-1));

        assertThat(response.value()).isEqualTo(-1);
        assertThat(response.score()).isEqualTo(3);
        assertThat(existing.getValue()).isEqualTo((short) -1);
        verify(userServiceI).adjustKarma(AUTHOR_ID, -2);
        verify(outboxServiceI, never()).enqueue(any(), any(), any(), any(), any());
    }

    @Test
    void removeVoteDeletesRow() {
        Vote existing = new Vote();
        existing.setUserId(USER_ID);
        existing.setTargetType(VoteTargetType.POST);
        existing.setTargetId(POST_ID);
        existing.setValue((short) 1);

        when(voteRepository.findByUserIdAndTargetTypeAndTargetId(USER_ID, VoteTargetType.POST, POST_ID))
                .thenReturn(Optional.of(existing));
        when(postServiceI.getById(POST_ID)).thenReturn(postResponse(1));
        when(postServiceI.applyVoteDelta(POST_ID, -1, -1, 0)).thenReturn(AUTHOR_ID);

        var response = service.votePost(POST_ID, new VoteRequestDTO(0));

        assertThat(response.value()).isZero();
        verify(voteRepository).delete(existing);
        verify(userServiceI).adjustKarma(AUTHOR_ID, -1);
    }

    private static PostResponseDTO postResponse(long score) {
        return new PostResponseDTO(
                POST_ID, UUID.randomUUID(), AUTHOR_ID, "t", null, null,
                score, 0, 0, 0, 0D, LocalDateTime.now()
        );
    }

    private static void setAuthenticatedUser(UUID userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId.toString(),
                "n/a",
                List.of(new SimpleGrantedAuthority("SCOPE_BASIC"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
