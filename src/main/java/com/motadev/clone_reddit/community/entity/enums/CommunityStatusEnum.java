package com.motadev.clone_reddit.community.entity.enums;

import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;

public enum CommunityStatusEnum {

    ACTIVE(1L, "ACTIVE"),
    ARCHIVED(2L, "ARCHIVED"),
    BANNED(3L, "BANNED");

    private final Long id;
    private final String name;

    CommunityStatusEnum(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public static CommunityStatusEnum fromId(Long id) {
        for (CommunityStatusEnum value : values()) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        throw new ResourceNotFoundException("No CommunityStatusEnum with id " + id);
    }

    public static CommunityStatusEnum fromName(String name) {
        for (CommunityStatusEnum value : values()) {
            if (value.name.equals(name)) {
                return value;
            }
        }
        throw new ResourceNotFoundException("No CommunityStatusEnum with name " + name);
    }
}