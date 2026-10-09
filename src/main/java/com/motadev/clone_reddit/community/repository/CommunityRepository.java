package com.motadev.clone_reddit.community.repository;

import com.motadev.clone_reddit.community.entity.Community;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommunityRepository extends JpaRepository<Community, UUID> {

    boolean existsByNameOrSlug(String name, String slug);

    @EntityGraph(attributePaths = {"topic", "type"})
    Page<Community> findByDeletedAtIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"topic", "type"})
    Optional<Community> findByCommunityIdAndDeletedAtIsNull(UUID communityId);

    @EntityGraph(attributePaths = {"topic", "type"})
    Optional<Community> findBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlug(String slug);

    boolean existsByName(String name);

    boolean existsByNameAndDeletedAtIsNotNull(String name);

    boolean existsBySlugAndDeletedAtIsNotNull(String slug);

    Optional<Community> findByCommunityId(UUID communityId);

    @Query("SELECT c FROM Community c WHERE c.deletedAt IS NOT NULL AND c.deletedAt <= :cutoff")
    List<Community> findCandidatesForPurge(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @EntityGraph(attributePaths = {"topic", "type"})
    @Query(value = """
            SELECT c FROM Community c
              JOIN CommunityMembership m ON m.id.communityId = c.communityId
             WHERE m.id.userId = :userId
               AND m.deactivatedAt IS NULL
               AND c.deletedAt IS NULL
             ORDER BY m.joinedAt DESC
            """,
            countQuery = """
            SELECT COUNT(c) FROM Community c
              JOIN CommunityMembership m ON m.id.communityId = c.communityId
             WHERE m.id.userId = :userId
               AND m.deactivatedAt IS NULL
               AND c.deletedAt IS NULL
            """)
    Page<Community> findJoinedBy(@Param("userId") UUID userId, Pageable pageable);

    @Modifying
    @Query(value = "UPDATE tb_community SET member_count = member_count + 1 WHERE community_id = :communityId",
            nativeQuery = true)
    int incrementMemberCount(@Param("communityId") UUID communityId);

    @Modifying
    @Query(value = "UPDATE tb_community SET member_count = member_count - 1 WHERE community_id = :communityId",
            nativeQuery = true)
    int decrementMemberCount(@Param("communityId") UUID communityId);

    @Modifying
    @Query(value = """
            UPDATE tb_community
               SET deleted_at = :deletedAt,
                   deleted_by = :ownerId
             WHERE owner_id = :ownerId
               AND deleted_at IS NULL
            """, nativeQuery = true)
    int softDeleteAllOwnedBy(@Param("ownerId") UUID ownerId, @Param("deletedAt") LocalDateTime deletedAt);
}
