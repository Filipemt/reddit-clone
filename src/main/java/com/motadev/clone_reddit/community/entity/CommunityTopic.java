package com.motadev.clone_reddit.community.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@Table(
        name = "tb_community_topics",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_community_topic_name", columnNames = "name")
        }
)
public class CommunityTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(length = 50, nullable = false)
    private String name;
}
