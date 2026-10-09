package com.motadev.clone_reddit.community.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "tb_community_membership")
public class CommunityMembership {

    @EmbeddedId
    private CommunityMembershipId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "role_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_membership_role")
    )
    private CommunityMemberRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "deactivated_at", nullable = true)
    private LocalDateTime deactivatedAt;
}
