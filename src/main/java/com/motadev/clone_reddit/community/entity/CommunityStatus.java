package com.motadev.clone_reddit.community.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
        name = "tb_community_status",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_community_status_name", columnNames = "name")
        }
)
public class CommunityStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "status_id", nullable = false)
    private Long statusId;

    @Column(length = 45, nullable = false)
    private String name;
}
