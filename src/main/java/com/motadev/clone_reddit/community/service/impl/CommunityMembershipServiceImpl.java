package com.motadev.clone_reddit.community.service.impl;

import com.motadev.clone_reddit.community.entity.Community;
import com.motadev.clone_reddit.community.entity.enums.CommunityMemberRoleEnum;
import com.motadev.clone_reddit.community.entity.enums.CommunityTypeEnum;
import com.motadev.clone_reddit.community.logging.CommunityEventLog;
import com.motadev.clone_reddit.community.repository.CommunityMembershipRepository;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import com.motadev.clone_reddit.community.service.CommunityMembershipServiceI;
import com.motadev.clone_reddit.shared.exception.ForbiddenException;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CommunityMembershipServiceImpl implements CommunityMembershipServiceI {

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final CommunityEventLog communityEventLog;

    public CommunityMembershipServiceImpl(CommunityRepository communityRepository,
                                          CommunityMembershipRepository communityMembershipRepository,
                                          AuthenticatedUserProvider authenticatedUserProvider,
                                          CommunityEventLog communityEventLog) {
        this.communityRepository = communityRepository;
        this.communityMembershipRepository = communityMembershipRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.communityEventLog = communityEventLog;
    }

    @Override
    @Transactional
    public void join(UUID communityId) {
        UUID userId = authenticatedUserProvider.extractUserIdFromAuthentication();
        Community community = findActiveOrThrow(communityId);

        CommunityTypeEnum type = CommunityTypeEnum.fromId(community.getType().getTypeId());
        if (type == CommunityTypeEnum.PRIVATE) {
            communityEventLog.membershipJoinForbidden(communityId, userId, type.getName());
            throw new ForbiddenException("You are not allowed to join this community.");
        }

        int inserted = communityMembershipRepository.insertIfAbsent(
                communityId, userId, CommunityMemberRoleEnum.MEMBER.getId());
        if (inserted == 0) {
            communityEventLog.membershipJoinAlreadyMember(communityId, userId);
            return;
        }

        communityRepository.incrementMemberCount(communityId);
        communityEventLog.membershipJoinSuccess(communityId, userId);
    }

    @Override
    @Transactional
    public void leave(UUID communityId) {
        UUID userId = authenticatedUserProvider.extractUserIdFromAuthentication();
        Community community = findActiveOrThrow(communityId);

        if (community.getOwnerId().equals(userId)) {
            communityEventLog.membershipLeaveOwnerForbidden(communityId, userId);
            throw new ForbiddenException("The community owner cannot leave the community.");
        }

        int deleted = communityMembershipRepository.deleteActive(
                communityId, userId, CommunityMemberRoleEnum.MEMBER.getId());
        if (deleted == 0) {
            communityEventLog.membershipLeaveNotMember(communityId, userId);
            return;
        }

        communityRepository.decrementMemberCount(communityId);
        communityEventLog.membershipLeaveSuccess(communityId, userId);
    }

    private Community findActiveOrThrow(UUID communityId) {
        return communityRepository.findByCommunityIdAndDeletedAtIsNull(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community not found."));
    }
}
