package com.motadev.clone_reddit.comment.service.impl;

import com.motadev.clone_reddit.auth.logging.AuthEventLog;
import com.motadev.clone_reddit.comment.converter.CommentConverter;
import com.motadev.clone_reddit.comment.dtos.request.CreateCommentRequestDTO;
import com.motadev.clone_reddit.comment.dtos.response.CommentResponseDTO;
import com.motadev.clone_reddit.comment.entity.Comment;
import com.motadev.clone_reddit.comment.logging.CommentEventLog;
import com.motadev.clone_reddit.comment.repository.CommentRepository;
import com.motadev.clone_reddit.messaging.outbox.service.OutboxServiceI;
import com.motadev.clone_reddit.post.service.PostServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID POST_AUTHOR_ID = UUID.randomUUID();
    private static final UUID POST_ID = UUID.randomUUID();
    private static final UUID PARENT_ID = UUID.randomUUID();

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostServiceI postServiceI;
    @Mock
    private OutboxServiceI outboxServiceI;

    private CommentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommentServiceImpl(
                commentRepository,
                new CommentConverter(),
                new AuthenticatedUserProvider(new AuthEventLog()),
                postServiceI,
                outboxServiceI,
                new CommentEventLog()
        );
        setAuthenticatedUser(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTopLevelCommentNotifiesPostAuthor() {
        when(postServiceI.requireActiveAuthorId(POST_ID)).thenReturn(POST_AUTHOR_ID);
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setCommentId(UUID.randomUUID());
            return comment;
        });

        service.create(POST_ID, new CreateCommentRequestDTO("hello", null));

        verify(postServiceI).adjustCommentCount(POST_ID, 1);
        verify(outboxServiceI).enqueue(
                eq("comment"),
                any(),
                eq("notification.post.commented"),
                eq("notification.post.commented"),
                anyMap()
        );
    }

    @Test
    void createReplyNotifiesParentAuthor() {
        Comment parent = new Comment();
        parent.setCommentId(PARENT_ID);
        parent.setPostId(POST_ID);
        parent.setAuthorId(UUID.randomUUID());

        when(postServiceI.requireActiveAuthorId(POST_ID)).thenReturn(POST_AUTHOR_ID);
        when(commentRepository.findByCommentIdAndDeletedAtIsNull(PARENT_ID)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setCommentId(UUID.randomUUID());
            return comment;
        });

        service.create(POST_ID, new CreateCommentRequestDTO("reply", PARENT_ID));

        verify(outboxServiceI).enqueue(
                eq("comment"),
                any(),
                eq("notification.comment.replied"),
                eq("notification.comment.replied"),
                anyMap()
        );
    }

    @Test
    void createRejectsParentFromAnotherPost() {
        Comment parent = new Comment();
        parent.setCommentId(PARENT_ID);
        parent.setPostId(UUID.randomUUID());
        parent.setAuthorId(UUID.randomUUID());

        when(postServiceI.requireActiveAuthorId(POST_ID)).thenReturn(POST_AUTHOR_ID);
        when(commentRepository.findByCommentIdAndDeletedAtIsNull(PARENT_ID)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.create(POST_ID, new CreateCommentRequestDTO("reply", PARENT_ID)))
                .isInstanceOf(ResourceInvalidException.class);
        verify(commentRepository, never()).save(any());
        verify(postServiceI, never()).adjustCommentCount(any(), anyLong());
    }

    @Test
    void listTreeBuildsNestedReplies() {
        Comment root = comment(UUID.randomUUID(), null, "root");
        Comment child = comment(UUID.randomUUID(), root.getCommentId(), "child");
        Comment grand = comment(UUID.randomUUID(), child.getCommentId(), "grand");

        when(postServiceI.requireActiveAuthorId(POST_ID)).thenReturn(POST_AUTHOR_ID);
        when(commentRepository.findByPostIdOrderByCreatedAtAsc(POST_ID)).thenReturn(List.of(root, child, grand));

        List<CommentResponseDTO> tree = service.listTreeByPost(POST_ID);

        assertThat(tree).hasSize(1);
        assertThat(tree.getFirst().replies()).hasSize(1);
        assertThat(tree.getFirst().replies().getFirst().replies()).hasSize(1);
        assertThat(tree.getFirst().replies().getFirst().replies().getFirst().body()).isEqualTo("grand");
    }

    private Comment comment(UUID id, UUID parentId, String body) {
        Comment comment = new Comment();
        comment.setCommentId(id);
        comment.setPostId(POST_ID);
        comment.setParentId(parentId);
        comment.setAuthorId(USER_ID);
        comment.setBody(body);
        return comment;
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
