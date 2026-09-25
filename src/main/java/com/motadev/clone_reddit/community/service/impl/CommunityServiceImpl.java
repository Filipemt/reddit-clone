package com.motadev.clone_reddit.community.service.impl;

import com.motadev.clone_reddit.community.converter.CommunityConverter;
import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import com.motadev.clone_reddit.community.service.CommunityServiceI;
import com.motadev.clone_reddit.shared.exception.UnauthorizedException;
import com.motadev.clone_reddit.user.service.UserServiceI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class CommunityServiceImpl implements CommunityServiceI {

    private final CommunityRepository communityRepository;
    private final UserServiceI userServiceI;
    private final CommunityConverter communityConverter;

    public CommunityServiceImpl(CommunityRepository communityRepository,
                                UserServiceI userServiceI,
                                CommunityConverter communityConverter) {
        this.communityRepository = communityRepository;
        this.userServiceI = userServiceI;
        this.communityConverter = communityConverter;
    }

    @Override
    public void create(CreateCommunityRequestDTO createCommunityRequestDTO) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("User is not authenticated.");
        }

        UUID userId = UUID.fromString(auth.getName());
        var owner = userServiceI.getUserById(userId);

        communityRepository.save(
                communityConverter.toEntity(createCommunityRequestDTO, owner.userId())
        );
    }
}
