package com.motadev.clone_reddit.community.service.impl;

import com.motadev.clone_reddit.community.logging.CommunityEventLog;
import com.motadev.clone_reddit.community.repository.CommunityMembershipRepository;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import com.motadev.clone_reddit.user.service.UserAccountDeletionHandler;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CommunityAccountDeletionHandler implements UserAccountDeletionHandler {

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final CommunityEventLog communityEventLog;

    public CommunityAccountDeletionHandler(CommunityRepository communityRepository,
                                           CommunityMembershipRepository communityMembershipRepository,
                                           CommunityEventLog communityEventLog) {
        this.communityRepository = communityRepository;
        this.communityMembershipRepository = communityMembershipRepository;
        this.communityEventLog = communityEventLog;
    }

    @Override
    @Transactional
    public void onAccountDeleted(UUID userId) {
        LocalDateTime now = LocalDateTime.now();

        int membershipCount = communityMembershipRepository.deactivateAllAndDecrementMemberCounts(userId, now);
        int communityCount = communityRepository.softDeleteAllOwnedBy(userId, now);

        communityEventLog.membershipDeactivated(userId, membershipCount);
        communityEventLog.ownerAccountDeleted(userId, communityCount);
    }
}
