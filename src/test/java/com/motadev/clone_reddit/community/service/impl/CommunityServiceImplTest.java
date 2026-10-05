package com.motadev.clone_reddit.community.service.impl;

import com.motadev.clone_reddit.community.converter.CommunityConverter;
import com.motadev.clone_reddit.community.dtos.request.CreateCommunityRequestDTO;
import com.motadev.clone_reddit.community.dtos.response.CommunityResponseDTO;
import com.motadev.clone_reddit.community.entity.Community;
import com.motadev.clone_reddit.community.entity.CommunityStatus;
import com.motadev.clone_reddit.community.entity.CommunityTopic;
import com.motadev.clone_reddit.community.entity.CommunityType;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import com.motadev.clone_reddit.media.dtos.response.MediaResponse;
import com.motadev.clone_reddit.media.service.MediaServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceAlreadyExists;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import com.motadev.clone_reddit.user.dtos.response.UserResponseDTO;
import com.motadev.clone_reddit.user.service.UserServiceI;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
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

    private CommunityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommunityServiceImpl(communityRepository, userServiceI,
                new CommunityConverter(entityManager), new AuthenticatedUserProvider(), mediaServiceI);

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
        when(mediaServiceI.upload(any(MultipartFile.class), contains("communities/icons/")))
                .thenReturn(new MediaResponse(ICON_MEDIA_ID, "https://signed/icon"));
        when(mediaServiceI.upload(any(MultipartFile.class), contains("communities/banners/")))
                .thenReturn(new MediaResponse(BANNER_MEDIA_ID, "https://signed/banner"));

        CommunityResponseDTO response = service.create(request(), file("icon.png"), file("banner.png"));

        assertThat(response.icon().mediaId()).isEqualTo(ICON_MEDIA_ID);
        assertThat(response.icon().url()).isEqualTo("https://signed/icon");
        assertThat(response.banner().mediaId()).isEqualTo(BANNER_MEDIA_ID);
        assertThat(response.banner().url()).isEqualTo("https://signed/banner");
        verify(communityRepository).saveAndFlush(any(Community.class));
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
        when(mediaServiceI.upload(any(MultipartFile.class), contains("communities/icons/")))
                .thenReturn(new MediaResponse(ICON_MEDIA_ID, "https://signed/icon"));
        MultipartFile emptyBanner = emptyFile();

        CommunityResponseDTO response = service.create(request(), file("icon.png"), emptyBanner);

        assertThat(response.banner()).isNull();
        verify(mediaServiceI, never()).upload(eq(emptyBanner), anyString());
    }

    @Test
    void deveResolverOParticionamentoMensalPorChamada() {
        // Se a pasta fosse uma constante static final, o yyyy/MM ficaria congelado no
        // carregamento da classe e so mudaria junto com um restart da aplicacao.
        prepareOwner();
        when(mediaServiceI.upload(any(MultipartFile.class), argThat(this::isMonthlyIconsFolder)))
                .thenReturn(new MediaResponse(ICON_MEDIA_ID, "https://signed/icon"));

        service.create(request(), file("icon.png"), null);

        verify(mediaServiceI).upload(any(MultipartFile.class),
                argThat(folder -> isMonthlyIconsFolder((String) folder)));
    }

    private boolean isMonthlyIconsFolder(String folder) {
        return folder.matches("communities/icons/\\d{4}/\\d{2}");
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
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_BASIC")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
