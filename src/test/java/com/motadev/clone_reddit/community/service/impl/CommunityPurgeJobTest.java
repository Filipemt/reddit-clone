package com.motadev.clone_reddit.community.service.impl;

import com.motadev.clone_reddit.community.config.CommunityPurgeProperties;
import com.motadev.clone_reddit.community.entity.Community;
import com.motadev.clone_reddit.community.logging.CommunityEventLog;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import com.motadev.clone_reddit.media.service.MediaServiceI;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityPurgeJobTest {

    private static final long ADVISORY_LOCK_ID = 123456789L;
    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(?)";

    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private MediaServiceI mediaServiceI;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private TransactionTemplate transactionTemplate;

    private CommunityPurgeJob communityPurgeJob;

    @BeforeEach
    void setUp() {
        communityPurgeJob = jobWith(true);
    }

    @Test
    void purge_whenDisabled_skipsLockAndRepository() {
        communityPurgeJob = jobWith(false);

        communityPurgeJob.purge();

        verifyNoInteractions(jdbcTemplate, communityRepository, mediaServiceI, transactionTemplate);
    }

    @Test
    void purge_whenLockNotAcquired_skipsWorkAndDoesNotUnlock() {
        when(jdbcTemplate.queryForObject(TRY_LOCK_SQL, Boolean.class, ADVISORY_LOCK_ID)).thenReturn(false);

        communityPurgeJob.purge();

        verify(jdbcTemplate, never()).queryForObject(UNLOCK_SQL, Boolean.class, ADVISORY_LOCK_ID);
        verifyNoInteractions(communityRepository, mediaServiceI, transactionTemplate);
    }

    @Test
    void purge_whenLockAcquired_deletesCandidatesAndReleasesLock() {
        UUID iconMediaId = UUID.randomUUID();
        UUID bannerMediaId = UUID.randomUUID();
        Community community = new Community();
        community.setIconMediaId(iconMediaId);
        community.setBannerMediaId(bannerMediaId);

        when(jdbcTemplate.queryForObject(TRY_LOCK_SQL, Boolean.class, ADVISORY_LOCK_ID)).thenReturn(true);
        when(communityRepository.findCandidatesForPurge(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(community));
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(transactionTemplate).execute(any());

        communityPurgeJob.purge();

        InOrder inOrder = inOrder(communityRepository, mediaServiceI);
        inOrder.verify(communityRepository).delete(community);
        inOrder.verify(mediaServiceI).deleteAfterCommit(List.of(iconMediaId, bannerMediaId));
        verify(jdbcTemplate).queryForObject(UNLOCK_SQL, Boolean.class, ADVISORY_LOCK_ID);
    }

    private CommunityPurgeJob jobWith(boolean enabled) {
        return new CommunityPurgeJob(
                new CommunityPurgeProperties(30, 100, enabled, ADVISORY_LOCK_ID),
                communityRepository,
                mediaServiceI,
                jdbcTemplate,
                transactionTemplate,
                new CommunityEventLog()
        );
    }
}
