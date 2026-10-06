package com.motadev.clone_reddit.community.service.impl;

import com.motadev.clone_reddit.community.converter.CommunityConverter;
import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;
import com.motadev.clone_reddit.community.dtos.response.CommunityResponseDTO;
import com.motadev.clone_reddit.community.entity.Community;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import com.motadev.clone_reddit.community.service.CommunityServiceI;
import com.motadev.clone_reddit.media.dtos.response.MediaResponse;
import com.motadev.clone_reddit.media.service.MediaServiceI;
import com.motadev.clone_reddit.shared.dtos.response.PagedResponseDTO;
import com.motadev.clone_reddit.shared.exception.ResourceAlreadyExists;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import com.motadev.clone_reddit.user.service.UserServiceI;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

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

    private static final Sort SORT_NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private static final int FALLBACK_PAGE_SIZE = 20;

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
        return communityConverter.toResponseDto(saved, urlsOf(iconMedia, bannerMedia));
    }

    @Override
    @Transactional
    public PagedResponseDTO<CommunityResponseDTO> list(Pageable pageable) {
        Page<Community> page = communityRepository.findByDeletedAtIsNull(withSortNewestFirst(pageable));

        Map<UUID, String> urls = urlsFor(mediaIdsOf(page.getContent()));

        return PagedResponseDTO.from(
                page,
                community -> communityConverter.toResponseDto(community, urls)
        );
    }

    @Override
    @Transactional
    public CommunityResponseDTO getById(UUID communityId) {
        Community community = communityRepository.findByCommunityIdAndDeletedAtIsNull(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community not found."));

        return communityConverter.toResponseDto(community, urlsFor(mediaIdsOf(community)));
    }

    @Override
    @Transactional
    public CommunityResponseDTO getBySlug(String slug) {
        Community community = communityRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Community not found."));

        return communityConverter.toResponseDto(community, urlsFor(mediaIdsOf(community)));
    }

    private Map<UUID, String> urlsFor(Collection<UUID> mediaIds) {
        return mediaIds.isEmpty() ? Map.of() : mediaServiceI.getUrls(mediaIds);
    }

    private static Pageable withSortNewestFirst(Pageable pageable) {
        if (!pageable.isPaged()) {
            return PageRequest.of(0, FALLBACK_PAGE_SIZE, SORT_NEWEST_FIRST);
        }

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), SORT_NEWEST_FIRST);
    }

    private Collection<UUID> mediaIdsOf(Community community) {
        return mediaIdsOf(List.of(community));
    }

    private Collection<UUID> mediaIdsOf(List<Community> communities) {
        return communities.stream()
                .flatMap(community -> Stream.of(community.getIconMediaId(), community.getBannerMediaId()))
                .filter(Objects::nonNull)
                .toList();
    }

    private static String communityIconFolder() {
        return "communities/icons/" + LocalDateTime.now().format(FOLDER_PERIOD_FORMATTER);
    }

    private static String communityBannerFolder() {
        return "communities/banners/" + LocalDateTime.now().format(FOLDER_PERIOD_FORMATTER);
    }

    private void validateCommunityUniqueness(CreateCommunityRequestDTO createCommunityRequestDTO) {
        if (communityRepository.existsByNameOrSlug(createCommunityRequestDTO.name(), createCommunityRequestDTO.slug())) {
            log.atWarn()                    .addKeyValue("event", "community.create.conflict")
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

    private Map<UUID, String> urlsOf(MediaResponse... media) {
        Map<UUID, String> urls = new HashMap<>();
        for (MediaResponse item : media) {
            if (item != null) {
                urls.put(item.mediaId(), item.url());
            }
        }

        return urls;
    }
}
