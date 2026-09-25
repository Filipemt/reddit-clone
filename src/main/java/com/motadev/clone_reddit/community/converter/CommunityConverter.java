package com.motadev.clone_reddit.community.converter;

import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;
import com.motadev.clone_reddit.community.entity.Community;
import com.motadev.clone_reddit.community.entity.CommunityStatus;
import com.motadev.clone_reddit.community.entity.CommunityTopic;
import com.motadev.clone_reddit.community.entity.CommunityType;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CommunityConverter {

    private final EntityManager entityManager;

    public CommunityConverter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Community toEntity(CreateCommunityRequestDTO dto, UUID owner) {
        Community community = new Community();
        community.setName(dto.name());
        community.setSlug(dto.slug());
        community.setDescription(dto.description());
        community.setTopic(entityManager.getReference(CommunityTopic.class, dto.topicId()));
        community.setType(entityManager.getReference(CommunityType.class, dto.typeId()));
        community.setStatus(entityManager.getReference(CommunityStatus.class, dto.statusId()));
        community.setOwner(owner);
        community.setMedia(null);
        community.setBannerMedia(null);

        return community;
    }
}
