package com.motadev.clone_reddit.community.service.impl;

import com.motadev.clone_reddit.community.converter.CommunityConverter;
import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;
import com.motadev.clone_reddit.community.dtos.response.CommunityResponseDTO;
import com.motadev.clone_reddit.community.entity.Community;
import com.motadev.clone_reddit.community.entity.CommunityStatus;
import com.motadev.clone_reddit.community.entity.CommunityTopic;
import com.motadev.clone_reddit.community.entity.CommunityType;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import com.motadev.clone_reddit.community.service.CommunityMembershipServiceI;
import com.motadev.clone_reddit.auth.logging.AuthEventLog;
import com.motadev.clone_reddit.community.logging.CommunityEventLog;
import com.motadev.clone_reddit.media.dtos.response.MediaResponse;
import com.motadev.clone_reddit.media.service.MediaServiceI;
import com.motadev.clone_reddit.shared.dtos.response.PagedResponseDTO;
import com.motadev.clone_reddit.shared.exception.ResourceAlreadyExists;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import com.motadev.clone_reddit.shared.exception.ForbiddenException;
import com.motadev.clone_reddit.user.dtos.response.UserResponseDTO;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import com.motadev.clone_reddit.user.service.UserServiceI;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ICON_MEDIA_ID = UUID.randomUUID();
    private static final UUID BANNER_MEDIA_ID = UUID.randomUUID();

    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private UserServiceI userServiceI;
    @Mock
    private MediaServiceI mediaServiceI;
    @Mock
    private EntityManager entityManager;
    @Mock
    private CommunityMembershipServiceI communityMembershipServiceI;

    private CommunityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommunityServiceImpl(communityRepository, userServiceI,
                new CommunityConverter(entityManager), new AuthenticatedUserProvider(new AuthEventLog()), mediaServiceI,
                new CommunityEventLog(), communityMembershipServiceI);

        setAuthenticatedUser(USER_ID);
        stubReferences();

        lenient().when(communityRepository.saveAndFlush(any(Community.class)))
                .thenAnswer(invocation -> savedCommunity(invocation.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveCriarComunidadeComIconeEBanner() {
        prepareOwner();
        when(mediaServiceI.upload(any(MultipartFile.class), endsWith("/icon")))
                .thenReturn(new MediaResponse(ICON_MEDIA_ID, "https://signed/icon"));
        when(mediaServiceI.upload(any(MultipartFile.class), endsWith("/banner")))
                .thenReturn(new MediaResponse(BANNER_MEDIA_ID, "https://signed/banner"));

        CommunityResponseDTO response = service.create(request(), file("icon.png"), file("banner.png"));

        assertThat(response.icon().mediaId()).isEqualTo(ICON_MEDIA_ID);
        assertThat(response.icon().url()).isEqualTo("https://signed/icon");
        assertThat(response.banner().mediaId()).isEqualTo(BANNER_MEDIA_ID);
        assertThat(response.banner().url()).isEqualTo("https://signed/banner");
        verify(communityRepository).saveAndFlush(any(Community.class));
    }

    @Test
    void deveRegistrarODonoComoModeradorAoCriar() {
        prepareOwner();

        CommunityResponseDTO response = service.create(request(), null, null);

        verify(communityMembershipServiceI).registerOwner(response.communityId(), USER_ID);
        assertThat(response.memberCount()).isEqualTo(1L);
        assertThat(response.isMember()).isTrue();
    }

    @Test
    void deveCriarComunidadeSemArquivos() {
        prepareOwner();

        CommunityResponseDTO response = service.create(request(), null, null);

        assertThat(response.icon()).isNull();
        assertThat(response.banner()).isNull();
        verify(mediaServiceI, never()).upload(any(MultipartFile.class), anyString());
    }

    @Test
    void naoDeveFazerUploadQuandoNomeOuSlugJaExiste() {
        when(communityRepository.existsByNameOrSlug(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(), file("icon.png"), file("banner.png")))
                .isInstanceOf(ResourceAlreadyExists.class);

        // A unicidade precisa ser validada antes de qualquer upload, senao a
        // compensacao do S3 e acionada sem necessidade.
        verify(mediaServiceI, never()).upload(any(MultipartFile.class), anyString());
        verify(communityRepository, never()).saveAndFlush(any(Community.class));
    }

    @Test
    void naoDeveFazerUploadQuandoParteVaziaForEnviada() {
        prepareOwner();
        when(mediaServiceI.upload(any(MultipartFile.class), endsWith("/icon")))
                .thenReturn(new MediaResponse(ICON_MEDIA_ID, "https://signed/icon"));
        MultipartFile emptyBanner = emptyFile();

        CommunityResponseDTO response = service.create(request(), file("icon.png"), emptyBanner);

        assertThat(response.banner()).isNull();
        verify(mediaServiceI, never()).upload(eq(emptyBanner), anyString());
    }

    @Test
    void deveGravarAMidiaSobAPastaDaPropriaComunidade() {
        // A chave precisa carregar o communityId para que a purga e a listagem
        // de objetos por comunidade sejam um prefixo, sem varrer o bucket.
        prepareOwner();
        when(mediaServiceI.upload(any(MultipartFile.class), endsWith("/icon")))
                .thenReturn(new MediaResponse(ICON_MEDIA_ID, "https://signed/icon"));
        when(mediaServiceI.upload(any(MultipartFile.class), endsWith("/banner")))
                .thenReturn(new MediaResponse(BANNER_MEDIA_ID, "https://signed/banner"));

        CommunityResponseDTO response = service.create(request(), file("icon.png"), file("banner.png"));

        String communityId = response.communityId().toString();
        verify(mediaServiceI).upload(any(MultipartFile.class), eq("communities/" + communityId + "/icon"));
        verify(mediaServiceI).upload(any(MultipartFile.class), eq("communities/" + communityId + "/banner"));
    }

    @Test
    void deveSubstituirIconeDescartandoOMidiaAntigaAposCommit() {
        UUID communityId = UUID.randomUUID();
        UUID newIconId = UUID.randomUUID();
        setAuthenticatedUser(USER_ID);
        when(communityRepository.findByCommunityIdAndDeletedAtIsNull(communityId))
                .thenReturn(Optional.of(owned(communityId, ICON_MEDIA_ID, BANNER_MEDIA_ID)));
        when(mediaServiceI.upload(any(MultipartFile.class), endsWith("/icon")))
                .thenReturn(new MediaResponse(newIconId, "https://signed/new-icon"));

        CommunityResponseDTO response = service.replaceIcon(communityId, file("icon.png"));

        assertThat(response.icon().mediaId()).isEqualTo(newIconId);
        assertThat(response.banner().mediaId()).isEqualTo(BANNER_MEDIA_ID);
        verify(mediaServiceI).deleteAfterCommit(List.of(ICON_MEDIA_ID));
    }

    @Test
    void deveRemoverIconeSemApagarOBanner() {
        UUID communityId = UUID.randomUUID();
        setAuthenticatedUser(USER_ID);
        Community community = owned(communityId, ICON_MEDIA_ID, BANNER_MEDIA_ID);
        when(communityRepository.findByCommunityIdAndDeletedAtIsNull(communityId))
                .thenReturn(Optional.of(community));

        service.removeIcon(communityId);

        assertThat(community.getIconMediaId()).isNull();
        assertThat(community.getBannerMediaId()).isEqualTo(BANNER_MEDIA_ID);
        verify(mediaServiceI).deleteAfterCommit(List.of(ICON_MEDIA_ID));
    }

    @Test
    void naoDeveApagarMidiaAnteriorQuandoNaoHaviaIcone() {
        UUID communityId = UUID.randomUUID();
        setAuthenticatedUser(USER_ID);
        when(communityRepository.findByCommunityIdAndDeletedAtIsNull(communityId))
                .thenReturn(Optional.of(owned(communityId, null, BANNER_MEDIA_ID)));
        when(mediaServiceI.upload(any(MultipartFile.class), endsWith("/icon")))
                .thenReturn(new MediaResponse(UUID.randomUUID(), "https://signed/icon"));

        service.replaceIcon(communityId, file("icon.png"));

        verify(mediaServiceI).deleteAfterCommit(List.of());
    }

    @Test
    void naoDeveTrocarMidiaDeComunidadeDeOutroUsuario() {
        UUID communityId = UUID.randomUUID();
        setAuthenticatedUser(USER_ID);
        when(communityRepository.findByCommunityIdAndDeletedAtIsNull(communityId))
                .thenReturn(Optional.of(ownedBy(communityId, UUID.randomUUID(), ICON_MEDIA_ID, null)));

        assertThatThrownBy(() -> service.replaceIcon(communityId, file("icon.png")))
                .isInstanceOf(ForbiddenException.class);

        // Autorizar depois do upload deixaria um objeto orfao no bucket a cada
        // tentativa negada.
        verify(mediaServiceI, never()).upload(any(MultipartFile.class), anyString());
    }

    @Test
    void adminPodeTrocarMidiaDeComunidadeDeOutroUsuario() {
        UUID communityId = UUID.randomUUID();
        setAuthenticatedUser(USER_ID, RoleValues.ADMIN);
        when(communityRepository.findByCommunityIdAndDeletedAtIsNull(communityId))
                .thenReturn(Optional.of(ownedBy(communityId, UUID.randomUUID(), ICON_MEDIA_ID, null)));
        when(mediaServiceI.upload(any(MultipartFile.class), endsWith("/icon")))
                .thenReturn(new MediaResponse(UUID.randomUUID(), "https://signed/icon"));

        service.replaceIcon(communityId, file("icon.png"));

        verify(mediaServiceI).upload(any(MultipartFile.class), endsWith("/icon"));
    }

    @Test
    void naoDeveTrocarMidiaDeComunidadeRemovida() {
        UUID communityId = UUID.randomUUID();
        setAuthenticatedUser(USER_ID);
        when(communityRepository.findByCommunityIdAndDeletedAtIsNull(communityId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeBanner(communityId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(mediaServiceI, never()).deleteAfterCommit(anyCollection());
    }

    @Test
    void deveMarcarComunidadeComoRemovidaSemApagarALinha() {
        UUID communityId = UUID.randomUUID();
        setAuthenticatedUser(USER_ID);
        Community community = ownedBy(communityId, USER_ID, ICON_MEDIA_ID, BANNER_MEDIA_ID);
        when(communityRepository.findByCommunityId(communityId)).thenReturn(Optional.of(community));

        service.delete(communityId);

        assertThat(community.getDeletedAt()).isNotNull();
        assertThat(community.getDeletedBy()).isEqualTo(USER_ID);
        verify(communityRepository, never()).delete(any(Community.class));
        verify(communityRepository, never()).deleteAll();
    }

    @Test
    void deveManterAMidiaDaComunidadeRemovida() {
        UUID communityId = UUID.randomUUID();
        setAuthenticatedUser(USER_ID);
        Community community = ownedBy(communityId, USER_ID, ICON_MEDIA_ID, BANNER_MEDIA_ID);
        when(communityRepository.findByCommunityId(communityId)).thenReturn(Optional.of(community));

        service.delete(communityId);

        assertThat(community.getIconMediaId()).isEqualTo(ICON_MEDIA_ID);
        assertThat(community.getBannerMediaId()).isEqualTo(BANNER_MEDIA_ID);
        verify(mediaServiceI, never()).deleteAfterCommit(anyCollection());
    }

    @Test
    void naoDeveAlterarComunidadeJaRemovida() {
        UUID communityId = UUID.randomUUID();
        setAuthenticatedUser(USER_ID);
        Community community = ownedBy(communityId, USER_ID, ICON_MEDIA_ID, null);
        when(communityRepository.findByCommunityId(communityId)).thenReturn(Optional.of(community));

        service.delete(communityId);
        LocalDateTime firstDeletedAt = community.getDeletedAt();
        service.delete(communityId);

        assertThat(community.getDeletedAt()).isEqualTo(firstDeletedAt);
        assertThat(community.getIconMediaId()).isEqualTo(ICON_MEDIA_ID);
        verify(mediaServiceI, never()).deleteAfterCommit(anyCollection());
    }

    @Test
    void deveMarcarQuemRemoveuComoAdminQuandoNaoEOwner() {
        UUID communityId = UUID.randomUUID();
        setAuthenticatedUser(USER_ID, RoleValues.ADMIN);
        Community community = ownedBy(communityId, UUID.randomUUID(), null, null);
        when(communityRepository.findByCommunityId(communityId)).thenReturn(Optional.of(community));

        service.delete(communityId);

        assertThat(community.getDeletedAt()).isNotNull();
        assertThat(community.getDeletedBy()).isEqualTo(USER_ID);
    }

    @Test
    void naoDeveRemoverComunidadeDeOutroUsuario() {
        UUID communityId = UUID.randomUUID();
        setAuthenticatedUser(USER_ID);
        Community community = ownedBy(communityId, UUID.randomUUID(), ICON_MEDIA_ID, null);
        when(communityRepository.findByCommunityId(communityId)).thenReturn(Optional.of(community));

        assertThatThrownBy(() -> service.delete(communityId))
                .isInstanceOf(ForbiddenException.class);

        assertThat(community.getDeletedAt()).isNull();
        verify(mediaServiceI, never()).deleteAfterCommit(anyCollection());
    }

    @Test
    void naoDeveRemoverComunidadeInexistente() {
        UUID communityId = UUID.randomUUID();
        setAuthenticatedUser(USER_ID);
        when(communityRepository.findByCommunityId(communityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(communityId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deveInformarNoLogQuandoOConflitoVemDeComunidadeRemovida() {
        when(communityRepository.existsByNameOrSlug(anyString(), anyString())).thenReturn(true);
        when(communityRepository.existsBySlugAndDeletedAtIsNotNull("java")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(), null, null))
                .isInstanceOf(ResourceAlreadyExists.class);

        // A resposta ao cliente e a mesma nos dois casos; so o log separa um nome
        // ativo de um nome ainda reservado pela retencao.
        verify(communityRepository).existsByNameAndDeletedAtIsNotNull("Java");
    }

    private Community owned(UUID communityId, UUID iconMediaId, UUID bannerMediaId) {
        return ownedBy(communityId, USER_ID, iconMediaId, bannerMediaId);
    }

    private Community ownedBy(UUID communityId, UUID ownerId, UUID iconMediaId, UUID bannerMediaId) {
        Community community = persisted("java", iconMediaId, bannerMediaId);
        community.setCommunityId(communityId);
        community.setOwnerId(ownerId);
        return community;
    }

    private boolean isMonthlyIconsFolder(String folder) {
        return folder.matches("communities/icons/\\d{4}/\\d{2}");
    }

    @Test
    void deveListarComUmaUnicaConsultaDeUrlsParaTodaAPagina() {
        UUID otherIconId = UUID.randomUUID();
        Community first = persisted("java", ICON_MEDIA_ID, BANNER_MEDIA_ID);
        Community second = persisted("kotlin", otherIconId, null);
        when(communityRepository.findByDeletedAtIsNull(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(mediaServiceI.getUrls(anyCollection()))
                .thenReturn(Map.of(ICON_MEDIA_ID, "https://signed/icon",
                        BANNER_MEDIA_ID, "https://signed/banner",
                        otherIconId, "https://signed/other"));

        PagedResponseDTO<CommunityResponseDTO> page = service.list(PageRequest.of(1, 20));

        assertThat(page.content()).hasSize(2);
        assertThat(page.content().get(0).icon().url()).isEqualTo("https://signed/icon");
        assertThat(page.content().get(1).icon().url()).isEqualTo("https://signed/other");
        assertThat(page.content().get(1).banner()).isNull();

        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(mediaServiceI).getUrls(captor.capture());
        assertThat(Set.copyOf(captor.getValue()))
                .containsExactlyInAnyOrder(ICON_MEDIA_ID, BANNER_MEDIA_ID, otherIconId);
    }

    @Test
    void deveResolverIsMemberDaPaginaInteiraEmUmaUnicaConsulta() {
        Community joined = persisted("java", null, null);
        Community notJoined = persisted("kotlin", null, null);
        when(communityRepository.findByDeletedAtIsNull(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(joined, notJoined)));
        when(communityMembershipServiceI.findJoinedCommunityIds(eq(USER_ID), anyCollection()))
                .thenReturn(Set.of(joined.getCommunityId()));

        PagedResponseDTO<CommunityResponseDTO> page = service.list(PageRequest.of(0, 20));

        assertThat(page.content().get(0).isMember()).isTrue();
        assertThat(page.content().get(1).isMember()).isFalse();
        verify(communityMembershipServiceI).findJoinedCommunityIds(eq(USER_ID), anyCollection());
    }

    @Test
    void deveInformarSeOUsuarioSegueAComunidadeNoDetalhe() {
        Community community = persisted("java", null, null);
        when(communityRepository.findByCommunityIdAndDeletedAtIsNull(community.getCommunityId()))
                .thenReturn(Optional.of(community));
        when(communityMembershipServiceI.findJoinedCommunityIds(USER_ID, List.of(community.getCommunityId())))
                .thenReturn(Set.of(community.getCommunityId()));

        CommunityResponseDTO response = service.getById(community.getCommunityId());

        assertThat(response.isMember()).isTrue();
    }

    @Test
    void deveListarAsComunidadesQueOUsuarioSegue() {
        Community community = persisted("java", null, null);
        when(communityRepository.findJoinedBy(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(community)));

        PagedResponseDTO<CommunityResponseDTO> page = service.listJoined(PageRequest.of(0, 20, Sort.by("name")));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).isMember()).isTrue();

        // A ordem vem do ORDER BY joinedAt da query; um sort vindo do cliente
        // seria concatenado a ela.
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(communityRepository).findJoinedBy(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    void naoDeveConsultarUrlsQuandoNenhumaComunidadeDaPaginaTemMidia() {
        when(communityRepository.findByDeletedAtIsNull(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(persisted("java", null, null))));

        PagedResponseDTO<CommunityResponseDTO> page = service.list(PageRequest.of(1, 20));

        assertThat(page.content().get(0).icon()).isNull();
        verify(mediaServiceI, never()).getUrls(anyCollection());
    }

    @Test
    void deveOrdenarListagemPorCreatedAtDecrescente() {
        when(communityRepository.findByDeletedAtIsNull(any(Pageable.class)))
                .thenReturn(Page.empty());

        service.list(PageRequest.of(1, 20));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(communityRepository).findByDeletedAtIsNull(captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("createdAt"))
                .isEqualTo(Sort.Order.desc("createdAt"));
    }

    @Test
    void deveBuscarDetalhePorId() {
        when(communityRepository.findByCommunityIdAndDeletedAtIsNull(any(UUID.class)))
                .thenReturn(Optional.of(persisted("java", ICON_MEDIA_ID, BANNER_MEDIA_ID)));
        when(mediaServiceI.getUrls(anyCollection()))
                .thenReturn(Map.of(ICON_MEDIA_ID, "https://signed/icon",
                        BANNER_MEDIA_ID, "https://signed/banner"));

        CommunityResponseDTO response = service.getById(UUID.randomUUID());

        assertThat(response.slug()).isEqualTo("java");
        assertThat(response.banner().url()).isEqualTo("https://signed/banner");
    }

    @Test
    void deveBuscarDetalhePorSlug() {
        when(communityRepository.findBySlugAndDeletedAtIsNull("java"))
                .thenReturn(Optional.of(persisted("java", null, null)));

        CommunityResponseDTO response = service.getBySlug("java");

        assertThat(response.name()).isEqualTo("JAVA");
        assertThat(response.icon()).isNull();
    }

    @Test
    void naoDeveEncontrarComunidadeInexistentePorId() {
        when(communityRepository.findByCommunityIdAndDeletedAtIsNull(any(UUID.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void naoDeveEncontrarComunidadeInexistentePorSlug() {
        when(communityRepository.findBySlugAndDeletedAtIsNull("nao-existe"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBySlug("nao-existe"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deveConsultarBuscaQueIgnoraComunidadesRemovidas() {
        when(communityRepository.findByDeletedAtIsNull(any(Pageable.class)))
                .thenReturn(Page.empty());

        service.list(PageRequest.of(1, 20));

        // findAll traria as communities removidas junto; o filtro precisa ficar
        // na query, e nao em memoria depois do fetch.
        verify(communityRepository).findByDeletedAtIsNull(any(Pageable.class));
        verify(communityRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void deveLimitarAPaginaQuandoRecebePageableSemPaginacao() {
        when(communityRepository.findByDeletedAtIsNull(any(Pageable.class)))
                .thenReturn(Page.empty());

        service.list(Pageable.unpaged());

        // Sem paginar, o repositorio devolveria a tabela inteira. O servico
        // precisa cair no tamanho default em vez de deixar isso passar.
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(communityRepository).findByDeletedAtIsNull(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt"))
                .isEqualTo(Sort.Order.desc("createdAt"));
    }

    private Community persisted(String slug, UUID iconMediaId, UUID bannerMediaId) {
        Community community = new Community();
        community.setCommunityId(UUID.randomUUID());
        community.setName(slug.toUpperCase());
        community.setSlug(slug);
        community.setDescription("Comunidade sobre " + slug);
        community.setTopic(reference(new CommunityTopic(), slug + " topic"));
        community.setType(reference(new CommunityType(), slug + " type"));
        community.setIconMediaId(iconMediaId);
        community.setBannerMediaId(bannerMediaId);
        community.setCreatedAt(LocalDateTime.now());
        return community;
    }

    private void prepareOwner() {
        when(communityRepository.existsByNameOrSlug(anyString(), anyString())).thenReturn(false);
        when(userServiceI.getUserById(USER_ID))
                .thenReturn(new UserResponseDTO(USER_ID, "motadev", "motadev@dev.com", 0));
    }

    private void stubReferences() {
        // CommunityConverter usa getReference para as entidades de referencia e o
        // toResponseDto le topic/type do resultado, entao o mock precisa devolver
        // instancias em vez de null.
        lenient().when(entityManager.getReference(eq(CommunityTopic.class), anyLong()))
                .thenReturn(reference(new CommunityTopic(), "programming"));
        lenient().when(entityManager.getReference(eq(CommunityType.class), anyLong()))
                .thenReturn(reference(new CommunityType(), "subreddit"));
        lenient().when(entityManager.getReference(eq(CommunityStatus.class), anyLong()))
                .thenReturn(reference(new CommunityStatus(), "active"));
    }

    private CommunityTopic reference(CommunityTopic topic, String name) {
        topic.setTopicId(1L);
        topic.setName(name);
        return topic;
    }

    private CommunityType reference(CommunityType type, String name) {
        type.setTypeId(1L);
        type.setName(name);
        return type;
    }

    private CommunityStatus reference(CommunityStatus status, String name) {
        status.setStatusId(1L);
        status.setName(name);
        return status;
    }

    private Community savedCommunity(Community community) {
        community.setCommunityId(UUID.randomUUID());
        community.setCreatedAt(LocalDateTime.now());
        return community;
    }

    private CreateCommunityRequestDTO request() {
        return new CreateCommunityRequestDTO("Java", "java", "Comunidade sobre Java", 1L, 1L, 1L);
    }

    private MultipartFile file(String originalFilename) {
        return new MockMultipartFile("file", originalFilename, "image/png", new byte[10]);
    }

    private MultipartFile emptyFile() {
        return new MockMultipartFile("file", "banner.png", "image/png", new byte[0]);
    }

    private void setAuthenticatedUser(UUID userId) {
        setAuthenticatedUser(userId, RoleValues.BASIC);
    }

    private void setAuthenticatedUser(UUID userId, RoleValues role) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null,
                List.of(new SimpleGrantedAuthority("SCOPE_" + role.name())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
