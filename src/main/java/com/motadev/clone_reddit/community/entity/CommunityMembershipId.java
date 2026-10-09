package com.motadev.clone_reddit.community.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@Getter
@Setter
public class CommunityMembershipId implements Serializable {

    @Column(name = "community_id", nullable = false)
    private UUID communityId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected CommunityMembershipId() {
    }

    public CommunityMembershipId(UUID communityId, UUID userId) {
        this.communityId = communityId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CommunityMembershipId that)) {
            return false;
        }
        return Objects.equals(communityId, that.communityId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(communityId, userId);
    }
}
