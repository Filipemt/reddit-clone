package com.motadev.clone_reddit.community.service.impl;

import com.motadev.clone_reddit.community.converter.CommunityConverter;
import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;
import com.motadev.clone_reddit.community.dtos.response.CommunityResponseDTO;
import com.motadev.clone_reddit.community.entity.Community;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import com.motadev.clone_reddit.community.service.CommunityServiceI;
import com.motadev.clone_reddit.media.dtos.response.MediaResponse;
import com.motadev.clone_reddit.media.service.MediaServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceAlreadyExists;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import com.motadev.clone_reddit.user.service.UserServiceI;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
public class CommunityServiceImpl implements CommunityServiceI {

    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final UserServiceI userServiceI;
    private final MediaServiceI mediaServiceI;
    private final CommunityConverter communityConverter;
    private final CommunityRepository communityRepository;

    private static final DateTimeFormatter FOLDER_PERIOD_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM");

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

        validateCommunityUniqueness(createCommunityRequestDTO);
        var owner = userServiceI.getUserById(userId);

        MediaResponse iconMedia = uploadIfPresent(iconFile, communityIconFolder());
        MediaResponse bannerMedia = uploadIfPresent(bannerFile, communityBannerFolder());

        Community saved = communityRepository.saveAndFlush(
                communityConverter.toEntity(
                        createCommunityRequestDTO,
                        owner.userId(),
                        idOf(iconMedia),
                        idOf(bannerMedia)
                )
        );
        return communityConverter.toResponseDto(saved, urlOf(iconMedia), urlOf(bannerMedia));
    }

    private static String communityIconFolder() {
        return "communities/icons/" + LocalDateTime.now().format(FOLDER_PERIOD_FORMATTER);
    }

    private static String communityBannerFolder() {
        return "communities/banners/" + LocalDateTime.now().format(FOLDER_PERIOD_FORMATTER);
    }

    private void validateCommunityUniqueness(CreateCommunityRequestDTO createCommunityRequestDTO) {
        if (communityRepository.existsByNameOrSlug(createCommunityRequestDTO.name(), createCommunityRequestDTO.slug())) {
            log.atWarn()
                    .addKeyValue("event", "community.create.conflict")
                    .addKeyValue("name", createCommunityRequestDTO.name())
                    .addKeyValue("slug", createCommunityRequestDTO.slug())
                    .setMessage("Attempt to create a community with an existing name or slug")
                    .log();
            throw new ResourceAlreadyExists("Resource Already Exists.");
        }
    }

    private MediaResponse uploadIfPresent(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        return mediaServiceI.upload(file, folder);
    }

    private UUID idOf(MediaResponse media) {
        return media == null ? null : media.mediaId();
    }

    private String urlOf(MediaResponse media) {
        return media == null ? null : media.url();
    }
}
