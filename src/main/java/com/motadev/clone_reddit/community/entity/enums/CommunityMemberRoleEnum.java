package com.motadev.clone_reddit.community.entity.enums;

import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;

public enum CommunityMemberRoleEnum {

    MEMBER(1L, "MEMBER"),
    MODERATOR(2L, "MODERATOR");

    private final Long id;
    private final String name;

    CommunityMemberRoleEnum(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public static CommunityMemberRoleEnum fromId(Long id) {
        for (CommunityMemberRoleEnum value : values()) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        throw new ResourceNotFoundException("No CommunityMemberRoleEnum with id " + id);
    }

    public static CommunityMemberRoleEnum fromName(String name) {
        for (CommunityMemberRoleEnum value : values()) {
            if (value.name.equals(name)) {
                return value;
            }
        }
        throw new ResourceNotFoundException("No CommunityMemberRoleEnum with name " + name);
    }
}
