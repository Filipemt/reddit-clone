package com.motadev.clone_reddit.community.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
        name = "tb_community_member_roles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_community_member_role_name", columnNames = "name")
        }
)
public class CommunityMemberRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(length = 30, nullable = false)
    private String name;
}
