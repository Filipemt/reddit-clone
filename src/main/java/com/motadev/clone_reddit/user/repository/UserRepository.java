package com.motadev.clone_reddit.user.repository;

import com.motadev.clone_reddit.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsernameAndIsActiveTrue(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Modifying
    @Query(value = """
            UPDATE tb_users
               SET karma = karma + :delta
             WHERE user_id = :userId
               AND is_active = true
            """, nativeQuery = true)
    int adjustKarma(@Param("userId") UUID userId, @Param("delta") long delta);
}
