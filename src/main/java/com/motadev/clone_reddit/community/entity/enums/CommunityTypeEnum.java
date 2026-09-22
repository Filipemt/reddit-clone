package com.motadev.clone_reddit.community.entity.enums;

import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;

public enum CommunityTypeEnum {

    PUBLIC(1L, "PUBLIC"),
    PRIVATE(2L, "PRIVATE"),
    RESTRICTED(3L, "RESTRICTED");

    private final Long id;
    private final String name;

    CommunityTypeEnum(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public static CommunityTypeEnum fromId(Long id) {
        for (CommunityTypeEnum value : values()) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        throw new ResourceNotFoundException("No CommunityTypeEnum with id " + id);
    }

    public static CommunityTypeEnum fromName(String name) {
        for (CommunityTypeEnum value : values()) {
            if (value.name.equals(name)) {
                return value;
            }
        }
        throw new ResourceNotFoundException("No CommunityTypeEnum with name " + name);
    }
}