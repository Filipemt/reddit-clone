package com.motadev.clone_reddit.user.repository;

import com.motadev.clone_reddit.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsernameAndIsActiveTrue(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
