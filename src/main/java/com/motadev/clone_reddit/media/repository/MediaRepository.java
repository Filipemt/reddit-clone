package com.motadev.clone_reddit.media.repository;

import com.motadev.clone_reddit.media.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

@Repository
public interface MediaRepository extends JpaRepository<Media, UUID> {

    @Query("select m.objectKey from Media m where m.objectKey in :objectKeys")
    Set<String> findExistingObjectKeys(@Param("objectKeys") Collection<String> objectKeys);
}
