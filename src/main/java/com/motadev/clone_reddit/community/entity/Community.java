package com.motadev.clone_reddit.community.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(
        name = "tb_community",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_community_name", columnNames = "name"),
                @UniqueConstraint(name = "uq_community_slug", columnNames = "slug")
        }
)
public class Community {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "community_id", nullable = false)
    private UUID communityId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "slug", length = 100, nullable = false)
    private String slug;

    @Column(name = "description", length = 255, nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "topic_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_community_topic")
    )
    private CommunityTopic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "type_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_community_type")
    )
    private CommunityType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "status_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_community_status")
    )
    private CommunityStatus status;

    @Column(name = "icon_media_id")
    private UUID iconMediaId;

    @Column(name = "banner_media_id")
    private UUID bannerMediaId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "member_count", nullable = false, updatable = false)
    private Long memberCount = 0L;

    @OneToMany(
            mappedBy = "community",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<CommunityRules> rules = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = true)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", nullable = true)
    private UUID deletedBy;
}
