package com.motadev.clone_reddit.community.service;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface CommunityMembershipServiceI {
    void join(UUID communityId);

    void leave(UUID communityId);

    void registerOwner(UUID communityId, UUID ownerId);

    Set<UUID> findJoinedCommunityIds(UUID userId, Collection<UUID> communityIds);
}
