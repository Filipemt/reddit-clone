package com.motadev.clone_reddit.community.service;

import java.util.UUID;

public interface CommunityMembershipServiceI {
    void join(UUID communityId);

    void leave(UUID communityId);
}
