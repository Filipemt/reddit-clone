package com.motadev.clone_reddit.vote.logging;

import com.motadev.clone_reddit.vote.entity.VoteTargetType;
import com.motadev.clone_reddit.vote.service.impl.VoteServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class VoteEventLog {

    private final Logger log = LoggerFactory.getLogger(VoteServiceImpl.class);

    public void castSuccess(UUID userId, VoteTargetType targetType, UUID targetId, int previous, int next) {
        log.atInfo()
                .addKeyValue("event", "vote.cast.success")
                .addKeyValue("userId", userId)
                .addKeyValue("targetType", targetType.name())
                .addKeyValue("targetId", targetId)
                .addKeyValue("previousValue", previous)
                .addKeyValue("value", next)
                .setMessage("Vote applied")
                .log();
    }

    public void castInvalid(UUID userId, int value) {
        log.atWarn()
                .addKeyValue("event", "vote.cast.invalid")
                .addKeyValue("userId", userId)
                .addKeyValue("value", value)
                .setMessage("Vote value must be -1, 0 or 1")
                .log();
    }
}
