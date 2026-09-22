package com.motadev.clone_reddit.community.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@Table(
        name = "tb_community_types",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_community_type_name", columnNames = "name")
        }
)
public class CommunityType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "type_id", nullable = false)
    private UUID typeId;

    @Column(length = 30, nullable = false)
    private String name;
}
