package com.motadev.clone_reddit.shared.security;

import com.motadev.clone_reddit.shared.exception.UnauthorizedException;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class AuthenticatedUserProvider {

    private static final String AUTHORITY_PREFIX = "SCOPE_";

    public UUID extractUserIdFromAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            log.atWarn()
                    .addKeyValue("event", "auth.user.unauthenticated")
                    .setMessage("Attempt to access a protected operation without authentication")
                    .log();
            throw new UnauthorizedException("User is not authenticated.");
        }

        return UUID.fromString(auth.getName());
    }

    public boolean hasRole(RoleValues role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        String expected = AUTHORITY_PREFIX + role.name();
        return auth.getAuthorities().stream()
                .anyMatch(granted -> expected.equals(granted.getAuthority()));
    }
}
