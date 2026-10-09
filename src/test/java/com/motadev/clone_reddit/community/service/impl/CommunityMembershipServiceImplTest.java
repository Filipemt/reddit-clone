package com.motadev.clone_reddit.community.service.impl;

import com.motadev.clone_reddit.auth.logging.AuthEventLog;
import com.motadev.clone_reddit.community.entity.Community;
import com.motadev.clone_reddit.community.entity.CommunityType;
import com.motadev.clone_reddit.community.entity.enums.CommunityMemberRoleEnum;
import com.motadev.clone_reddit.community.entity.enums.CommunityTypeEnum;
import com.motadev.clone_reddit.community.logging.CommunityEventLog;
import com.motadev.clone_reddit.community.repository.CommunityMembershipRepository;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import com.motadev.clone_reddit.shared.exception.ForbiddenException;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityMembershipServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final Long MEMBER_ROLE_ID = CommunityMemberRoleEnum.MEMBER.getId();

    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMembershipRepository communityMembershipRepository;

    private CommunityMembershipServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommunityMembershipServiceImpl(communityRepository, communityMembershipRepository,
                new AuthenticatedUserProvider(new AuthEventLog()), new CommunityEventLog());

        setAuthenticatedUser(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveEntrarNaComunidadePublicaEIncrementarContador() {
        givenActiveCommunity(CommunityTypeEnum.PUBLIC);
        when(communityMembershipRepository.insertIfAbsent(COMMUNITY_ID, USER_ID, MEMBER_ROLE_ID)).thenReturn(1);

        service.join(COMMUNITY_ID);

        verify(communityRepository).incrementMemberCount(COMMUNITY_ID);
    }

    @Test
    void deveEntrarNaComunidadeRestrita() {
        givenActiveCommunity(CommunityTypeEnum.RESTRICTED);
        when(communityMembershipRepository.insertIfAbsent(COMMUNITY_ID, USER_ID, MEMBER_ROLE_ID)).thenReturn(1);

        service.join(COMMUNITY_ID);

        verify(communityRepository).incrementMemberCount(COMMUNITY_ID);
    }

    @Test
    void naoDeveIncrementarContadorQuandoUsuarioJaEMembro() {
        givenActiveCommunity(CommunityTypeEnum.PUBLIC);
        when(communityMembershipRepository.insertIfAbsent(COMMUNITY_ID, USER_ID, MEMBER_ROLE_ID)).thenReturn(0);

        service.join(COMMUNITY_ID);

        verify(communityRepository, never()).incrementMemberCount(any(UUID.class));
    }

    @Test
    void naoDeveEntrarNaComunidadePrivada() {
        givenActiveCommunity(CommunityTypeEnum.PRIVATE);

        assertThatThrownBy(() -> service.join(COMMUNITY_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(communityMembershipRepository, never()).insertIfAbsent(any(UUID.class), any(UUID.class), anyLong());
        verify(communityRepository, never()).incrementMemberCount(any(UUID.class));
    }

    @Test
    void naoDeveEntrarEmComunidadeInexistenteOuRemovida() {
        when(communityRepository.findByCommunityIdAndDeletedAtIsNull(COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(COMMUNITY_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(communityMembershipRepository, never()).insertIfAbsent(any(UUID.class), any(UUID.class), anyLong());
    }

    @Test
    void deveSairDaComunidadeEDecrementarContador() {
        givenActiveCommunity(CommunityTypeEnum.PUBLIC);
        when(communityMembershipRepository.deleteActive(COMMUNITY_ID, USER_ID, MEMBER_ROLE_ID)).thenReturn(1);

        service.leave(COMMUNITY_ID);

        verify(communityRepository).decrementMemberCount(COMMUNITY_ID);
    }

    @Test
    void naoDeveDecrementarContadorQuandoUsuarioNaoEMembro() {
        givenActiveCommunity(CommunityTypeEnum.PUBLIC);
        when(communityMembershipRepository.deleteActive(COMMUNITY_ID, USER_ID, MEMBER_ROLE_ID)).thenReturn(0);

        service.leave(COMMUNITY_ID);

        verify(communityRepository, never()).decrementMemberCount(any(UUID.class));
    }

    @Test
    void naoDevePermitirQueODonoSaiaDaComunidade() {
        givenActiveCommunity(CommunityTypeEnum.PUBLIC);
        setAuthenticatedUser(OWNER_ID);

        assertThatThrownBy(() -> service.leave(COMMUNITY_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(communityMembershipRepository, never()).deleteActive(any(UUID.class), any(UUID.class), anyLong());
        verify(communityRepository, never()).decrementMemberCount(any(UUID.class));
    }

    @Test
    void naoDeveSairDeComunidadeInexistenteOuRemovida() {
        when(communityRepository.findByCommunityIdAndDeletedAtIsNull(COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leave(COMMUNITY_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(communityMembershipRepository, never()).deleteActive(any(UUID.class), any(UUID.class), anyLong());
    }

    @Test
    void deveRegistrarODonoComoModeradorSemIncrementarContador() {
        service.registerOwner(COMMUNITY_ID, OWNER_ID);

        verify(communityMembershipRepository)
                .insertIfAbsent(COMMUNITY_ID, OWNER_ID, CommunityMemberRoleEnum.MODERATOR.getId());
        verify(communityRepository, never()).incrementMemberCount(any(UUID.class));
    }

    @Test
    void naoDeveConsultarInscricoesQuandoNaoHaComunidades() {
        assertThat(service.findJoinedCommunityIds(USER_ID, List.of())).isEmpty();

        verify(communityMembershipRepository, never()).findActiveCommunityIds(any(UUID.class), anyCollection());
    }

    private void givenActiveCommunity(CommunityTypeEnum typeEnum) {
        CommunityType type = new CommunityType();
        type.setTypeId(typeEnum.getId());
        type.setName(typeEnum.getName());

        Community community = new Community();
        community.setCommunityId(COMMUNITY_ID);
        community.setOwnerId(OWNER_ID);
        community.setType(type);

        when(communityRepository.findByCommunityIdAndDeletedAtIsNull(COMMUNITY_ID)).thenReturn(Optional.of(community));
    }

    private void setAuthenticatedUser(UUID userId) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null,
                List.of(new SimpleGrantedAuthority("SCOPE_BASIC")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
