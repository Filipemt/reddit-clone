package com.motadev.clone_reddit.vote.repository;

import com.motadev.clone_reddit.vote.entity.Vote;
import com.motadev.clone_reddit.vote.entity.VoteTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

    Optional<Vote> findByUserIdAndTargetTypeAndTargetId(
            UUID userId,
            VoteTargetType targetType,
            UUID targetId
    );
}
