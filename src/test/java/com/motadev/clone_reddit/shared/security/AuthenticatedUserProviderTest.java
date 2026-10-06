package com.motadev.clone_reddit.shared.security;

import com.motadev.clone_reddit.auth.logging.AuthEventLog;
import com.motadev.clone_reddit.shared.exception.UnauthorizedException;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedUserProviderTest {

    private final AuthenticatedUserProvider provider = new AuthenticatedUserProvider(new AuthEventLog());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveReconhecerPapelAdminPeloPrefixoScope() {
        authenticate("SCOPE_ADMIN");

        assertThat(provider.hasRole(RoleValues.ADMIN)).isTrue();
        assertThat(provider.hasRole(RoleValues.BASIC)).isFalse();
    }

    @Test
    void naoDeveConfundirPrefixoRoleComScope() {
        // O token traz os papeis no claim "scope" e o resource server prefixa com
        // SCOPE_. Checar ROLE_ devolveria falso para todos, incluindo o admin.
        authenticate("ROLE_ADMIN");

        assertThat(provider.hasRole(RoleValues.ADMIN)).isFalse();
    }

    @Test
    void naoDeveTerPapelSemAutenticacao() {
        assertThat(provider.hasRole(RoleValues.ADMIN)).isFalse();
    }

    @Test
    void deveLancarUnauthorizedSemAutenticacao() {
        assertThatThrownBy(() -> provider.extractUserIdFromAuthentication())
                .isInstanceOf(UnauthorizedException.class);
    }

    private void authenticate(String... authorities) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), null,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
