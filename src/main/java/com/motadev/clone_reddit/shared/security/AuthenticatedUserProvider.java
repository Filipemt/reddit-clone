package com.motadev.clone_reddit.shared.security;

import com.motadev.clone_reddit.auth.logging.AuthEventLog;
import com.motadev.clone_reddit.shared.exception.UnauthorizedException;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthenticatedUserProvider {

    private static final String AUTHORITY_PREFIX = "SCOPE_";

    private final AuthEventLog authEventLog;

    public AuthenticatedUserProvider(AuthEventLog authEventLog) {
        this.authEventLog = authEventLog;
    }

    public UUID extractUserIdFromAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            authEventLog.userUnauthenticated();
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
