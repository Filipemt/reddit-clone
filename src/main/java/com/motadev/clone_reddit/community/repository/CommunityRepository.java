package com.motadev.clone_reddit.community.repository;

import com.motadev.clone_reddit.community.entity.Community;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommunityRepository extends JpaRepository<Community, UUID> {

    boolean existsByNameOrSlug(String name, String slug);

    @EntityGraph(attributePaths = {"topic", "type"})
    Page<Community> findByDeletedAtIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"topic", "type"})
    Optional<Community> findByCommunityIdAndDeletedAtIsNull(UUID communityId);

    @EntityGraph(attributePaths = {"topic", "type"})
    Optional<Community> findBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlug(String slug);

    boolean existsByName(String name);
}