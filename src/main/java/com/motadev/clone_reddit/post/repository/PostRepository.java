package com.motadev.clone_reddit.post.repository;

import com.motadev.clone_reddit.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    Optional<Post> findByPostIdAndDeletedAtIsNull(UUID postId);

    Page<Post> findByCommunityIdAndDeletedAtIsNull(UUID communityId, Pageable pageable);

    @Query("""
            SELECT p FROM Post p
             WHERE p.communityId = :communityId
               AND p.deletedAt IS NULL
               AND (:cutoff IS NULL OR p.createdAt >= :cutoff)
            """)
    Page<Post> findActiveByCommunityAndCreatedAtAfter(
            @Param("communityId") UUID communityId,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable
    );

    @Modifying
    @Query(value = """
            UPDATE tb_post
               SET hot_score = :hotScore
             WHERE post_id = :postId
               AND deleted_at IS NULL
            """, nativeQuery = true)
    int updateHotScore(@Param("postId") UUID postId, @Param("hotScore") double hotScore);

    @Query(value = """
            SELECT * FROM tb_post
             WHERE deleted_at IS NULL
               AND (
                    title ILIKE CONCAT('%', :q, '%')
                 OR body ILIKE CONCAT('%', :q, '%')
               )
             ORDER BY created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM tb_post
             WHERE deleted_at IS NULL
               AND (
                    title ILIKE CONCAT('%', :q, '%')
                 OR body ILIKE CONCAT('%', :q, '%')
               )
            """,
            nativeQuery = true)
    Page<Post> searchActiveIlike(@Param("q") String q, Pageable pageable);

    @Modifying
    @Query(value = """
            UPDATE tb_post
               SET comment_count = comment_count + :delta
             WHERE post_id = :postId
               AND deleted_at IS NULL
            """, nativeQuery = true)
    int adjustCommentCount(@Param("postId") UUID postId, @Param("delta") long delta);

    @Modifying
    @Query(value = """
            UPDATE tb_post
               SET score = score + :scoreDelta,
                   up_count = up_count + :upDelta,
                   down_count = down_count + :downDelta
             WHERE post_id = :postId
               AND deleted_at IS NULL
            """, nativeQuery = true)
    int applyVoteDelta(
            @Param("postId") UUID postId,
            @Param("scoreDelta") long scoreDelta,
            @Param("upDelta") long upDelta,
            @Param("downDelta") long downDelta
    );
}
