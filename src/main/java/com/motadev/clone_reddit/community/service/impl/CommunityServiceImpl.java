package com.motadev.clone_reddit.community.service.impl;

import com.motadev.clone_reddit.community.converter.CommunityConverter;
import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;
import com.motadev.clone_reddit.community.dtos.response.CommunityResponseDTO;
import com.motadev.clone_reddit.community.entity.Community;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import com.motadev.clone_reddit.community.service.CommunityServiceI;
import com.motadev.clone_reddit.media.service.MediaServiceI;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import com.motadev.clone_reddit.user.service.UserServiceI;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@Slf4j
public class CommunityServiceImpl implements CommunityServiceI {

    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final UserServiceI userServiceI;
    private final MediaServiceI mediaServiceI;
    private final CommunityConverter communityConverter;
    private final CommunityRepository communityRepository;

    private static final String COMMUNITY_ICON_FOLDER = "communities/icons";
    private static final String COMMUNITY_BANNER_FOLDER = "communities/banners";

    public CommunityServiceImpl(CommunityRepository communityRepository,
                                UserServiceI userServiceI,
                                CommunityConverter communityConverter,
                                AuthenticatedUserProvider authenticatedUserProvider,
                                MediaServiceI mediaServiceI) {
        this.communityRepository = communityRepository;
        this.userServiceI = userServiceI;
        this.communityConverter = communityConverter;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.mediaServiceI = mediaServiceI;
    }

    @Override
    @Transactional
    public CommunityResponseDTO create(CreateCommunityRequestDTO createCommunityRequestDTO,
                                       MultipartFile iconFile,
                                       MultipartFile bannerFile) {
        UUID userId = authenticatedUserProvider.extractUserIdFromAuthentication();
        var owner = userServiceI.getUserById(userId);

        UUID iconMediaId = uploadIfPresent(iconFile, COMMUNITY_ICON_FOLDER);
        UUID bannerMediaId = uploadIfPresent(bannerFile, COMMUNITY_BANNER_FOLDER);

        Community saved = communityRepository.saveAndFlush(
                communityConverter.toEntity(
                        createCommunityRequestDTO,
                        owner.userId(),
                        iconMediaId,
                        bannerMediaId
                )
        );
        return communityConverter.toResponseDto(saved);
    }

    private UUID uploadIfPresent(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        return mediaServiceI.upload(file, folder).mediaId();
    }
}
