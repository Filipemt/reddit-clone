package com.motadev.clone_reddit.user.entity.enums;

public enum RoleValues {
    BASIC(1L),
    ADMIN(2L);

    final long roleId;

    RoleValues(long roleId) {
        this.roleId = roleId;
    }
}
