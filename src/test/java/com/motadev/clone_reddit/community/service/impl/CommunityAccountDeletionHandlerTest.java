package com.motadev.clone_reddit.community.service.impl;

import com.motadev.clone_reddit.community.logging.CommunityEventLog;
import com.motadev.clone_reddit.community.repository.CommunityMembershipRepository;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityAccountDeletionHandlerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMembershipRepository communityMembershipRepository;

    private CommunityAccountDeletionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CommunityAccountDeletionHandler(communityRepository, communityMembershipRepository,
                new CommunityEventLog());
    }

    @Test
    void deveDesativarInscricoesAntesDeRemoverAsComunidadesDoDono() {
        when(communityMembershipRepository.deactivateAllAndDecrementMemberCounts(eq(USER_ID), any()))
                .thenReturn(3);
        when(communityRepository.softDeleteAllOwnedBy(eq(USER_ID), any())).thenReturn(1);

        handler.onAccountDeleted(USER_ID);

        InOrder order = inOrder(communityMembershipRepository, communityRepository);
        order.verify(communityMembershipRepository).deactivateAllAndDecrementMemberCounts(eq(USER_ID), any());
        order.verify(communityRepository).softDeleteAllOwnedBy(eq(USER_ID), any());
    }

    @Test
    void deveUsarOMesmoInstanteNasDuasOperacoes() {
        handler.onAccountDeleted(USER_ID);

        ArgumentCaptor<LocalDateTime> deactivatedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> deletedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(communityMembershipRepository).deactivateAllAndDecrementMemberCounts(eq(USER_ID), deactivatedAt.capture());
        verify(communityRepository).softDeleteAllOwnedBy(eq(USER_ID), deletedAt.capture());
        assertThat(deactivatedAt.getValue()).isEqualTo(deletedAt.getValue());
    }
}
