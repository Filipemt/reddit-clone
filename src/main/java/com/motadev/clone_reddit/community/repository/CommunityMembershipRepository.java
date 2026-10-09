package com.motadev.clone_reddit.community.repository;

import com.motadev.clone_reddit.community.entity.CommunityMembership;
import com.motadev.clone_reddit.community.entity.CommunityMembershipId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

@Repository
public interface CommunityMembershipRepository extends JpaRepository<CommunityMembership, CommunityMembershipId> {

    @Modifying
    @Query(value = """
            INSERT INTO tb_community_membership (community_id, user_id, role_id, joined_at)
            VALUES (:communityId, :userId, :roleId, now())
            ON CONFLICT (community_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("communityId") UUID communityId,
                       @Param("userId") UUID userId,
                       @Param("roleId") Long roleId);

    @Modifying
    @Query(value = """
            DELETE FROM tb_community_membership
             WHERE community_id = :communityId
               AND user_id = :userId
               AND role_id = :roleId
               AND deactivated_at IS NULL
            """, nativeQuery = true)
    int deleteActive(@Param("communityId") UUID communityId,
                     @Param("userId") UUID userId,
                     @Param("roleId") Long roleId);

    @Modifying
    @Query(value = """
            WITH deactivated AS (
                UPDATE tb_community_membership
                   SET deactivated_at = :deactivatedAt
                 WHERE user_id = :userId
                   AND deactivated_at IS NULL
                RETURNING community_id
            ), locked AS (
                SELECT community_id FROM tb_community
                 WHERE community_id IN (SELECT community_id FROM deactivated)
                 ORDER BY community_id
                   FOR UPDATE
            )
            UPDATE tb_community c
               SET member_count = c.member_count - 1
              FROM locked l
             WHERE c.community_id = l.community_id
            """, nativeQuery = true)
    int deactivateAllAndDecrementMemberCounts(@Param("userId") UUID userId,
                                              @Param("deactivatedAt") LocalDateTime deactivatedAt);

    @Query("""
            SELECT m.id.communityId FROM CommunityMembership m
             WHERE m.id.userId = :userId
               AND m.id.communityId IN :communityIds
               AND m.deactivatedAt IS NULL
            """)
    Set<UUID> findActiveCommunityIds(@Param("userId") UUID userId,
                                     @Param("communityIds") Collection<UUID> communityIds);
}
