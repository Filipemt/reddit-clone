package com.motadev.clone_reddit.vote.service.impl;

import com.motadev.clone_reddit.comment.service.CommentServiceI;
import com.motadev.clone_reddit.messaging.outbox.service.OutboxServiceI;
import com.motadev.clone_reddit.post.service.PostServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import com.motadev.clone_reddit.user.service.UserServiceI;
import com.motadev.clone_reddit.vote.dtos.request.VoteRequestDTO;
import com.motadev.clone_reddit.vote.dtos.response.VoteResponseDTO;
import com.motadev.clone_reddit.vote.entity.Vote;
import com.motadev.clone_reddit.vote.entity.VoteTargetType;
import com.motadev.clone_reddit.vote.logging.VoteEventLog;
import com.motadev.clone_reddit.vote.repository.VoteRepository;
import com.motadev.clone_reddit.vote.service.VoteServiceI;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class VoteServiceImpl implements VoteServiceI {

    private final VoteRepository voteRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final PostServiceI postServiceI;
    private final CommentServiceI commentServiceI;
    private final UserServiceI userServiceI;
    private final OutboxServiceI outboxServiceI;
    private final VoteEventLog voteEventLog;

    public VoteServiceImpl(
            VoteRepository voteRepository,
            AuthenticatedUserProvider authenticatedUserProvider,
            PostServiceI postServiceI,
            CommentServiceI commentServiceI,
            UserServiceI userServiceI,
            OutboxServiceI outboxServiceI,
            VoteEventLog voteEventLog
    ) {
        this.voteRepository = voteRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.postServiceI = postServiceI;
        this.commentServiceI = commentServiceI;
        this.userServiceI = userServiceI;
        this.outboxServiceI = outboxServiceI;
        this.voteEventLog = voteEventLog;
    }

    @Override
    @Transactional
    public VoteResponseDTO votePost(UUID postId, VoteRequestDTO dto) {
        return cast(VoteTargetType.POST, postId, dto.value());
    }

    @Override
    @Transactional
    public VoteResponseDTO voteComment(UUID commentId, VoteRequestDTO dto) {
        return cast(VoteTargetType.COMMENT, commentId, dto.value());
    }

    private VoteResponseDTO cast(VoteTargetType targetType, UUID targetId, int requested) {
        UUID userId = authenticatedUserProvider.extractUserIdFromAuthentication();
        if (requested < -1 || requested > 1) {
            voteEventLog.castInvalid(userId, requested);
            throw new ResourceInvalidException("Vote value must be -1, 0 or 1.");
        }

        Vote existing = voteRepository.findByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)
                .orElse(null);
        int previous = existing == null ? 0 : existing.getValue();
        int next = requested;

        long scoreBefore = readScore(targetType, targetId);
        if (previous == next) {
            return new VoteResponseDTO(targetType, targetId, next, scoreBefore);
        }

        long scoreDelta = next - previous;
        long upDelta = (next == 1 ? 1 : 0) - (previous == 1 ? 1 : 0);
        long downDelta = (next == -1 ? 1 : 0) - (previous == -1 ? 1 : 0);

        UUID authorId = applyTargetDelta(targetType, targetId, scoreDelta, upDelta, downDelta);
        userServiceI.adjustKarma(authorId, scoreDelta);

        if (next == 0) {
            if (existing != null) {
                voteRepository.delete(existing);
            }
        } else if (existing == null) {
            Vote vote = new Vote();
            vote.setUserId(userId);
            vote.setTargetType(targetType);
            vote.setTargetId(targetId);
            vote.setValue((short) next);
            voteRepository.save(vote);
        } else {
            existing.setValue((short) next);
        }

        if (next == 1 && previous != 1 && !authorId.equals(userId)) {
            outboxServiceI.enqueue(
                    "vote",
                    targetId,
                    "notification.vote.upvoted",
                    "notification.vote.upvoted",
                    Map.of(
                            "targetType", targetType.name(),
                            "targetId", targetId.toString(),
                            "voterId", userId.toString(),
                            "recipientId", authorId.toString()
                    )
            );
        }

        voteEventLog.castSuccess(userId, targetType, targetId, previous, next);
        return new VoteResponseDTO(targetType, targetId, next, scoreBefore + scoreDelta);
    }

    private UUID applyTargetDelta(
            VoteTargetType targetType,
            UUID targetId,
            long scoreDelta,
            long upDelta,
            long downDelta
    ) {
        return switch (targetType) {
            case POST -> postServiceI.applyVoteDelta(targetId, scoreDelta, upDelta, downDelta);
            case COMMENT -> commentServiceI.applyVoteDelta(targetId, scoreDelta, upDelta, downDelta);
        };
    }

    private long readScore(VoteTargetType targetType, UUID targetId) {
        return switch (targetType) {
            case POST -> postServiceI.getById(targetId).score();
            case COMMENT -> commentServiceI.requireActiveScore(targetId);
        };
    }
}
