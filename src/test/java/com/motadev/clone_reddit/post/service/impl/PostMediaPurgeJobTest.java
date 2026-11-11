package com.motadev.clone_reddit.post.service.impl;

import com.motadev.clone_reddit.media.service.MediaServiceI;
import com.motadev.clone_reddit.post.config.PostMediaPurgeProperties;
import com.motadev.clone_reddit.post.entity.Post;
import com.motadev.clone_reddit.post.logging.PostEventLog;
import com.motadev.clone_reddit.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostMediaPurgeJobTest {

    private static final long ADVISORY_LOCK_ID = 765432109L;
    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(?)";

    @Mock
    private PostRepository postRepository;
    @Mock
    private MediaServiceI mediaServiceI;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private TransactionTemplate transactionTemplate;

    private PostMediaPurgeJob postMediaPurgeJob;

    @BeforeEach
    void setUp() {
        postMediaPurgeJob = jobWith(true);
    }

    @Test
    void purge_whenDisabled_skipsLockAndRepository() {
        postMediaPurgeJob = jobWith(false);

        postMediaPurgeJob.purge();

        verifyNoInteractions(jdbcTemplate, postRepository, mediaServiceI, transactionTemplate);
    }

    @Test
    void purge_whenLockNotAcquired_skipsWorkAndDoesNotUnlock() {
        when(jdbcTemplate.queryForObject(TRY_LOCK_SQL, Boolean.class, ADVISORY_LOCK_ID)).thenReturn(false);

        postMediaPurgeJob.purge();

        verify(jdbcTemplate, never()).queryForObject(UNLOCK_SQL, Boolean.class, ADVISORY_LOCK_ID);
        verifyNoInteractions(postRepository, mediaServiceI, transactionTemplate);
    }

    @Test
    void purge_whenLockAcquired_clearsMediaAndCallsDeleteAfterCommit() {
        UUID mediaId = UUID.randomUUID();
        Post post = new Post();
        post.setMediaId(mediaId);

        when(jdbcTemplate.queryForObject(TRY_LOCK_SQL, Boolean.class, ADVISORY_LOCK_ID)).thenReturn(true);
        when(postRepository.findCandidatesForMediaPurge(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(post));
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(transactionTemplate).execute(any());

        postMediaPurgeJob.purge();

        assertThat(post.getMediaId()).isNull();
        InOrder inOrder = inOrder(postRepository, mediaServiceI);
        inOrder.verify(postRepository).save(post);
        inOrder.verify(mediaServiceI).deleteAfterCommit(List.of(mediaId));
        verify(jdbcTemplate).queryForObject(UNLOCK_SQL, Boolean.class, ADVISORY_LOCK_ID);
    }

    private PostMediaPurgeJob jobWith(boolean enabled) {
        return new PostMediaPurgeJob(
                new PostMediaPurgeProperties(30, 100, enabled, ADVISORY_LOCK_ID),
                postRepository,
                mediaServiceI,
                jdbcTemplate,
                transactionTemplate,
                new PostEventLog()
        );
    }
}
