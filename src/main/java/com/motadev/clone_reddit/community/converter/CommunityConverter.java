package com.motadev.clone_reddit.community.converter;

import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;
import com.motadev.clone_reddit.community.dtos.response.CommunityResponseDTO;
import com.motadev.clone_reddit.community.entity.Community;
import com.motadev.clone_reddit.community.entity.CommunityStatus;
import com.motadev.clone_reddit.community.entity.CommunityTopic;
import com.motadev.clone_reddit.community.entity.CommunityType;
import com.motadev.clone_reddit.media.dtos.response.MediaResponse;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CommunityConverter {

    private final EntityManager entityManager;

    public CommunityConverter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Community toEntity(CreateCommunityRequestDTO dto,
                              UUID owner, UUID iconMediaId,
                              UUID bannerMediaId) {
        Community community = new Community();
        community.setName(dto.name());
        community.setSlug(dto.slug());
        community.setDescription(dto.description());
        community.setTopic(entityManager.getReference(CommunityTopic.class, dto.topicId()));
        community.setType(entityManager.getReference(CommunityType.class, dto.typeId()));
        community.setStatus(entityManager.getReference(CommunityStatus.class, dto.statusId()));
        community.setOwnerId(owner);
        community.setIconMediaId(iconMediaId);
        community.setBannerMediaId(bannerMediaId);

        return community;
    }

    public CommunityResponseDTO toResponseDto(Community community,
                                              String iconUrl,
                                              String bannerUrl) {
        return new CommunityResponseDTO(
                community.getCommunityId(),
                community.getName(),
                community.getSlug(),
                community.getDescription(),
                community.getTopic().getTopicId(),
                community.getTopic().getName(),
                community.getType().getTypeId(),
                community.getType().getName(),
                toReference(community.getIconMediaId(), iconUrl),
                toReference(community.getBannerMediaId(), bannerUrl),
                community.getCreatedAt()
        );
    }

    private MediaResponse toReference(UUID mediaId, String url) {
        return mediaId == null ? null : new MediaResponse(mediaId, url);
    }
}
