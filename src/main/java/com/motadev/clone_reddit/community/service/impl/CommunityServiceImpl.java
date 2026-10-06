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
import com.motadev.clone_reddit.shared.exception.ForbiddenException;
import com.motadev.clone_reddit.shared.exception.ResourceAlreadyExists;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
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

        Community saved = communityRepository.saveAndFlush(
                communityConverter.toEntity(createCommunityRequestDTO, owner.userId(), null, null)
        );

        MediaResponse iconMedia = uploadIfPresent(iconFile, iconFolderOf(saved.getCommunityId()));
        MediaResponse bannerMedia = uploadIfPresent(bannerFile, bannerFolderOf(saved.getCommunityId()));

        saved.setIconMediaId(idOf(iconMedia));
        saved.setBannerMediaId(idOf(bannerMedia));

        return communityConverter.toResponseDto(saved, urlsOf(iconMedia, bannerMedia));
    }

    @Override
    @Transactional
    public CommunityResponseDTO replaceIcon(UUID communityId, MultipartFile iconFile) {
        Community community = findActiveOrThrow(communityId);

        MediaResponse media = mediaServiceI.upload(iconFile, iconFolderOf(communityId));

        return swapMediaSlot(community, media, true);
    }

    @Override
    @Transactional
    public CommunityResponseDTO replaceBanner(UUID communityId, MultipartFile bannerFile) {
        Community community = findActiveOrThrow(communityId);

        MediaResponse media = mediaServiceI.upload(bannerFile, bannerFolderOf(communityId));

        return swapMediaSlot(community, media, false);
    }

    @Override
    @Transactional
    public void removeIcon(UUID communityId) {
        clearMediaSlot(findActiveOrThrow(communityId), true);
    }

    @Override
    @Transactional
    public void removeBanner(UUID communityId) {
        clearMediaSlot(findActiveOrThrow(communityId), false);
    }

    private CommunityResponseDTO swapMediaSlot(Community community, MediaResponse media, boolean iconSlot) {
        UUID previousMediaId = mediaIdOf(community, iconSlot);

        if (iconSlot) {
            community.setIconMediaId(media.mediaId());
        } else {
            community.setBannerMediaId(media.mediaId());
        }

        mediaServiceI.deleteAfterCommit(idsOf(previousMediaId));

        return communityConverter.toResponseDto(community, urlsOf(media));
    }

    private void clearMediaSlot(Community community, boolean iconSlot) {
        mediaServiceI.deleteAfterCommit(idsOf(mediaIdOf(community, iconSlot)));

        if (iconSlot) {
            community.setIconMediaId(null);
        } else {
            community.setBannerMediaId(null);
        }
    }

    private static UUID mediaIdOf(Community community, boolean iconSlot) {
        return iconSlot ? community.getIconMediaId() : community.getBannerMediaId();
    }

    @Override
    @Transactional
    public void delete(UUID communityId) {
        Community community = communityRepository.findByCommunityId(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community not found."));
        UUID userId = authenticatedUserProvider.extractUserIdFromAuthentication();

        authorizeManagement(community);

        if (community.getDeletedAt() != null) {
            log.atDebug()
                    .addKeyValue("event", "community.delete.already_deleted")
                    .addKeyValue("communityId", communityId)
                    .addKeyValue("userId", userId)
                    .setMessage("Community was already soft deleted; nothing to do")
                    .log();
            return;
        }

        Collection<UUID> mediaIds = idsOf(community.getIconMediaId(), community.getBannerMediaId());

        community.setDeletedAt(LocalDateTime.now());
        community.setDeletedBy(userId);

        mediaServiceI.deleteAfterCommit(mediaIds);

        log.atInfo()
                .addKeyValue("event", "community.delete.success")
                .addKeyValue("communityId", communityId)
                .addKeyValue("userId", userId)
                .addKeyValue("isOwner", community.getOwnerId().equals(userId))
                .addKeyValue("mediaCount", mediaIds.size())
                .setMessage("Community soft deleted")
                .log();
    }

    private Community findActiveOrThrow(UUID communityId) {
        Community community = communityRepository.findByCommunityIdAndDeletedAtIsNull(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community not found."));

        authorizeManagement(community);

        return community;
    }

    private void authorizeManagement(Community community) {
        UUID userId = authenticatedUserProvider.extractUserIdFromAuthentication();

        if (community.getOwnerId().equals(userId) || authenticatedUserProvider.hasRole(RoleValues.ADMIN)) {
            return;
        }

        log.atWarn()
                .addKeyValue("event", "community.media.forbidden")
                .addKeyValue("communityId", community.getCommunityId())
                .addKeyValue("userId", userId)
                .setMessage("Attempt to change the media of a community the user does not own")
                .log();
        throw new ForbiddenException("You are not allowed to change this community.");
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

    private static String iconFolderOf(UUID communityId) {
        return "communities/" + communityId + "/icon";
    }

    private static String bannerFolderOf(UUID communityId) {
        return "communities/" + communityId + "/banner";
    }

    private void validateCommunityUniqueness(CreateCommunityRequestDTO createCommunityRequestDTO) {
        if (!communityRepository.existsByNameOrSlug(createCommunityRequestDTO.name(), createCommunityRequestDTO.slug())) {
            return;
        }

        boolean deleted = communityRepository.existsByNameAndDeletedAtIsNotNull(
                        createCommunityRequestDTO.name())
                || communityRepository.existsBySlugAndDeletedAtIsNotNull(createCommunityRequestDTO.slug());

        log.atWarn()
                .addKeyValue("event", "community.create.conflict")
                .addKeyValue("name", createCommunityRequestDTO.name())
                .addKeyValue("slug", createCommunityRequestDTO.slug())
                .addKeyValue("deleted", deleted)
                .setMessage("Attempt to create a community with an existing name or slug")
                .log();
        throw new ResourceAlreadyExists("Resource Already Exists.");
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

    private static List<UUID> idsOf(UUID... mediaIds) {
        return Stream.of(mediaIds).filter(Objects::nonNull).toList();
    }
}
