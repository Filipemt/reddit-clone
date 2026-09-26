package com.motadev.clone_reddit.community.service.impl;

import com.motadev.clone_reddit.community.converter.CommunityConverter;
import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;
import com.motadev.clone_reddit.community.dtos.response.CommunityResponseDTO;
import com.motadev.clone_reddit.community.entity.Community;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import com.motadev.clone_reddit.community.service.CommunityServiceI;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import com.motadev.clone_reddit.user.service.UserServiceI;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class CommunityServiceImpl implements CommunityServiceI {

    private final CommunityRepository communityRepository;
    private final UserServiceI userServiceI;
    private final CommunityConverter communityConverter;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public CommunityServiceImpl(CommunityRepository communityRepository,
                                UserServiceI userServiceI,
                                CommunityConverter communityConverter,
                                AuthenticatedUserProvider authenticatedUserProvider) {
        this.communityRepository = communityRepository;
        this.userServiceI = userServiceI;
        this.communityConverter = communityConverter;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @Override
    @Transactional
    public CommunityResponseDTO create(CreateCommunityRequestDTO createCommunityRequestDTO) {
        UUID userId = authenticatedUserProvider.extractUserIdFromAuthentication();
        var owner = userServiceI.getUserById(userId);

        Community saved = communityRepository.saveAndFlush(
                communityConverter.toEntity(createCommunityRequestDTO, owner.userId())
        );
        return communityConverter.toResponseDto(saved);
    }
}
