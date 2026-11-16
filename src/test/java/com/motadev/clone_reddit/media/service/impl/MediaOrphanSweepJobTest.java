package com.motadev.clone_reddit.media.service.impl;

import com.motadev.clone_reddit.media.config.MediaOrphanSweepProperties;
import com.motadev.clone_reddit.media.logging.MediaEventLog;
import com.motadev.clone_reddit.media.repository.MediaRepository;
import com.motadev.clone_reddit.media.service.S3ObjectStore;
import com.motadev.clone_reddit.media.service.S3ObjectStore.ListedObject;
import com.motadev.clone_reddit.media.service.S3ObjectStore.ObjectPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaOrphanSweepJobTest {

    private static final long ADVISORY_LOCK_ID = 765432109L;
    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(?)";
    private static final String BUCKET = "test-bucket";

    @Mock
    private S3ObjectStore s3ObjectStore;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private MediaOrphanSweepJob mediaOrphanSweepJob;

    @BeforeEach
    void setUp() {
        mediaOrphanSweepJob = jobWith(true);
    }

    @Test
    void sweep_whenDisabled_skipsLockAndCollaborators() {
        mediaOrphanSweepJob = jobWith(false);

        mediaOrphanSweepJob.sweep();

        verifyNoInteractions(jdbcTemplate, s3ObjectStore, mediaRepository);
    }

    @Test
    void sweep_whenLockNotAcquired_skipsWorkAndDoesNotUnlock() {
        when(jdbcTemplate.queryForObject(TRY_LOCK_SQL, Boolean.class, ADVISORY_LOCK_ID)).thenReturn(false);

        mediaOrphanSweepJob.sweep();

        verify(jdbcTemplate, never()).queryForObject(UNLOCK_SQL, Boolean.class, ADVISORY_LOCK_ID);
        verifyNoInteractions(s3ObjectStore, mediaRepository);
    }

    @Test
    void sweep_whenLockAcquired_deletesOnlyOrphansPastGrace() {
        Instant now = Instant.now();
        ListedObject orphanPastGrace = new ListedObject("orphans/old.png", now.minus(Duration.ofHours(2)));
        ListedObject knownPastGrace = new ListedObject("known/old.png", now.minus(Duration.ofHours(2)));
        ListedObject orphanInsideGrace = new ListedObject("orphans/fresh.png", now.minus(Duration.ofMinutes(10)));

        when(jdbcTemplate.queryForObject(TRY_LOCK_SQL, Boolean.class, ADVISORY_LOCK_ID)).thenReturn(true);
        when(s3ObjectStore.listObjects(isNull(), anyInt()))
                .thenReturn(new ObjectPage(List.of(orphanPastGrace, knownPastGrace, orphanInsideGrace), null));
        when(mediaRepository.findExistingObjectKeys(anyCollection()))
                .thenReturn(Set.of("known/old.png"));
        when(s3ObjectStore.deleteObjectQuietly("orphans/old.png")).thenReturn(true);
        when(s3ObjectStore.bucketName()).thenReturn(BUCKET);

        mediaOrphanSweepJob.sweep();

        verify(s3ObjectStore).deleteObjectQuietly("orphans/old.png");
        verify(s3ObjectStore, never()).deleteObjectQuietly("known/old.png");
        verify(s3ObjectStore, never()).deleteObjectQuietly("orphans/fresh.png");
        verify(jdbcTemplate).queryForObject(UNLOCK_SQL, Boolean.class, ADVISORY_LOCK_ID);
    }

    private MediaOrphanSweepJob jobWith(boolean enabled) {
        return new MediaOrphanSweepJob(
                new MediaOrphanSweepProperties(Duration.ofHours(1), 1000, enabled, ADVISORY_LOCK_ID),
                s3ObjectStore,
                mediaRepository,
                jdbcTemplate,
                new MediaEventLog()
        );
    }
}
