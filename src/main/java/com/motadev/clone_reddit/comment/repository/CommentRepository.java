package com.motadev.clone_reddit.comment.repository;

import com.motadev.clone_reddit.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByPostIdOrderByCreatedAtAsc(UUID postId);

    Optional<Comment> findByCommentIdAndDeletedAtIsNull(UUID commentId);

    @Modifying
    @Query(value = """
            UPDATE tb_comment
               SET score = score + :scoreDelta,
                   up_count = up_count + :upDelta,
                   down_count = down_count + :downDelta
             WHERE comment_id = :commentId
               AND deleted_at IS NULL
            """, nativeQuery = true)
    int applyVoteDelta(
            @Param("commentId") UUID commentId,
            @Param("scoreDelta") long scoreDelta,
            @Param("upDelta") long upDelta,
            @Param("downDelta") long downDelta
    );
}
