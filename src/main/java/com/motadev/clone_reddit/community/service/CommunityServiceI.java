package com.motadev.clone_reddit.community.service;

import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;
import com.motadev.clone_reddit.community.dtos.response.CommunityResponseDTO;
import com.motadev.clone_reddit.shared.dtos.response.PagedResponseDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface CommunityServiceI {
    CommunityResponseDTO create(CreateCommunityRequestDTO createCommunityRequestDTO, MultipartFile iconFile, MultipartFile bannerFile);

    PagedResponseDTO<CommunityResponseDTO> list(Pageable pageable);

    PagedResponseDTO<CommunityResponseDTO> listJoined(Pageable pageable);

    CommunityResponseDTO getById(UUID communityId);

    CommunityResponseDTO getBySlug(String slug);

    CommunityResponseDTO replaceIcon(UUID communityId, MultipartFile iconFile);

    CommunityResponseDTO replaceBanner(UUID communityId, MultipartFile bannerFile);

    void removeIcon(UUID communityId);

    void removeBanner(UUID communityId);

    void delete(UUID communityId);

    UUID requireActiveOwnerId(UUID communityId);
}