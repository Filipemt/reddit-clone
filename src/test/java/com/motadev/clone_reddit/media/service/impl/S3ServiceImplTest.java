package com.motadev.clone_reddit.media.service.impl;

import com.motadev.clone_reddit.media.converter.MediaConverter;
import com.motadev.clone_reddit.media.dtos.response.MediaResponse;
import com.motadev.clone_reddit.media.entity.Media;
import com.motadev.clone_reddit.media.repository.MediaRepository;
import com.motadev.clone_reddit.media.validator.MediaFileValidator;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ServiceImplTest {

    private static final String BUCKET = "reddit-clone-test";
    private static final String FOLDER = "communities/icons/2026/10";
    private static final String SIGNED_URL = "https://reddit-clone-test.s3.amazonaws.com/signed";

    @Mock
    private S3Client s3Client;
    @Mock
    private S3Presigner s3Presigner;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private MediaFileValidator mediaFileValidator;

    private S3ServiceImpl service;
    private PresignedGetObjectRequest presigned;

    @BeforeEach
    void setUp() {
        service = new S3ServiceImpl(s3Client, s3Presigner, mediaRepository,
                new MediaConverter(), mediaFileValidator);
        ReflectionTestUtils.setField(service, "bucketName", BUCKET);
        presigned = presignedWithUrl(SIGNED_URL);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deveEnviarArquivoValidadoEOsMetadadosParaOS3() {
        prepareSuccessfulUpload();

        MediaResponse response = service.upload(file("icon.png"), FOLDER);

        assertThat(response.url()).isEqualTo(SIGNED_URL);
        verify(mediaFileValidator).validateAndResolveExtension(any());
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(mediaRepository).save(any(Media.class));
    }

    @Test
    void devePropagarFalhaDeValidacaoSemGravarNoS3NemNoBanco() {
        when(mediaFileValidator.validateAndResolveExtension(any()))
                .thenThrow(new ResourceInvalidException("File extension is not allowed."));

        assertThatThrownBy(() -> service.upload(file("malware.exe"), FOLDER))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessage("File extension is not allowed.");

        verifyNoInteractions(s3Client);
        verifyNoInteractions(mediaRepository);
    }

    @Test
    void deveDeletarObjetoNoS3QuandoSalvarMediaFalhar() {
        prepareSuccessfulUpload();
        when(mediaRepository.save(any(Media.class)))
                .thenThrow(new IllegalStateException("tb_media indisponivel"));

        assertThatThrownBy(() -> service.upload(file("icon.png"), FOLDER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("tb_media indisponivel");

        // O objeto ja estava no bucket e o registro em tb_media nao existe, o que o
        // tornaria inalcancavel para qualquer compensacao posterior.
        verify(s3Client).deleteObject(deletedUploadedObject());
    }

    @Test
    void deveNaoRegistrarRollbackForaDeUmaTransacao() {
        prepareSuccessfulUpload();

        service.upload(file("icon.png"), FOLDER);

        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deveDeletarObjetoQuandoTransacaoForRevertida() {
        prepareSuccessfulUpload();
        TransactionSynchronizationManager.initSynchronization();

        service.upload(file("icon.png"), FOLDER);

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(s3Client).deleteObject(deletedUploadedObject());
    }

    @Test
    void naoDeveDeletarObjetoQuandoTransacaoForConfirmada() {
        prepareSuccessfulUpload();
        TransactionSynchronizationManager.initSynchronization();

        service.upload(file("icon.png"), FOLDER);

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void naoDevePropagarFalhaDoDeleteNaCompensacao() {
        prepareSuccessfulUpload();
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new IllegalStateException("AccessDenied"));
        TransactionSynchronizationManager.initSynchronization();

        service.upload(file("icon.png"), FOLDER);

        // afterCompletion roda de dentro do proxy transacional: uma excecao lancada
        // ali escaparia pelo proxy e mascararia a falha original da transacao.
        assertThatCode(() -> completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK))
                .doesNotThrowAnyException();
    }

    @Test
    void naoDeveDeletarObjetoQuandoDesfechoDaTransacaoForDesconhecido() {
        prepareSuccessfulUpload();
        TransactionSynchronizationManager.initSynchronization();

        service.upload(file("icon.png"), FOLDER);

        // Desfecho ambiguo: apagar arriscaria quebrar uma referencia que foi de fato
        // confirmada (falha visivel, sem recuperacao), enquanto manter arrisca apenas
        // um orfao. O orfao fica sinalizado no log media.upload.rollback_unknown.
        completeTransaction(TransactionSynchronization.STATUS_UNKNOWN);
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deveLancarNotFoundQuandoMidaNaoExisteAoGerarUrl() {
        when(mediaRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUrl(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Media not found");
    }

    @Test
    void deveAssinarTodaAPaginaDeMidiasComUmaUnicaConsulta() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        stubPresigner();
        when(mediaRepository.findAllById(any())).thenReturn(List.of(media(first), media(second)));

        Map<UUID, String> urls = service.getUrls(List.of(first, second));

        assertThat(urls).containsOnlyKeys(first, second);
        assertThat(urls.get(first)).isEqualTo(SIGNED_URL);
        // A listagem de comunidades traz icone e banner de cada linha: uma consulta
        // por midia viraria N+1 no banco, mesmo que a assinatura em si seja local.
        verify(mediaRepository).findAllById(any());
        verify(mediaRepository, never()).findById(any());
    }

    @Test
    void naoDeveConsultarOBancoQuandoNaoHaMidiasParaAssinar() {
        assertThat(service.getUrls(List.of())).isEmpty();
        assertThat(service.getUrls(null)).isEmpty();

        verifyNoInteractions(mediaRepository);
    }

    @Test
    void naoDeveFalharQuandoUmaMidiaDaPaginaNaoExisteMais() {
        UUID existent = UUID.randomUUID();
        stubPresigner();
        when(mediaRepository.findAllById(any())).thenReturn(List.of(media(existent)));

        Map<UUID, String> urls = service.getUrls(List.of(existent, UUID.randomUUID()));

        // Diferente do getUrl de midia unica, aqui a ausencia no mapa e o resultado
        // esperado: a listagem mostra o resto da pagina em vez de falhar inteira.
        assertThat(urls).containsOnlyKeys(existent);
    }

    @Test
    void deveDeletarObjetosDasMidiasDepoisDoCommitConfirmado() {
        when(mediaRepository.findAllById(any())).thenReturn(List.of(mediaWithKey("icon-old.png")));
        TransactionSynchronizationManager.initSynchronization();

        service.deleteAfterCommit(List.of(UUID.randomUUID()));
        completeCommit();

        verify(s3Client).deleteObject(deletedObject("icon-old.png"));
    }

    @Test
    void naoDeveDeletarObjetosQuandoATransacaoForRevertida() {
        when(mediaRepository.findAllById(any())).thenReturn(List.of(mediaWithKey("icon-old.png")));
        TransactionSynchronizationManager.initSynchronization();

        service.deleteAfterCommit(List.of(UUID.randomUUID()));
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        // Ao contrario do upload, aqui o rollback tem de preservar o objeto: a linha
        // em tb_media continua existindo e a referencia do dominio segue valendo.
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deveDeletarObjetosImediatamenteQuandoNaoHaTransacaoAtiva() {
        when(mediaRepository.findAllById(any())).thenReturn(List.of(mediaWithKey("banner-old.png")));

        service.deleteAfterCommit(List.of(UUID.randomUUID()));

        verify(s3Client).deleteObject(deletedObject("banner-old.png"));
    }

    @Test
    void naoDeveDeletarObjetosQuandoNenhumaMidiaExisteMais() {
        when(mediaRepository.findAllById(any())).thenReturn(List.of());

        service.deleteAfterCommit(List.of(UUID.randomUUID()));

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    private Media media(UUID mediaId) {
        Media media = new Media();
        media.setMediaId(mediaId);
        media.setBucket(BUCKET);
        media.setObjectKey(FOLDER + "/" + mediaId + ".png");
        return media;
    }

    private Media mediaWithKey(String objectKey) {
        Media media = new Media();
        media.setMediaId(UUID.randomUUID());
        media.setBucket(BUCKET);
        media.setObjectKey(FOLDER + "/" + objectKey);
        return media;
    }

    private DeleteObjectRequest deletedObject(String objectKey) {
        return argThat(request -> BUCKET.equals(request.bucket())
                && (FOLDER + "/" + objectKey).equals(request.key()));
    }

    private void completeCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> {
                    synchronization.afterCommit();
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
                });
    }

    private void prepareSuccessfulUpload() {
        when(mediaFileValidator.validateAndResolveExtension(any())).thenReturn(".png");

        lenient().when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> {
            Media media = invocation.getArgument(0);
            media.setMediaId(UUID.randomUUID());
            return media;
        });

        // getUrl relê a mídia por id para montar a requisição de assinatura.
        lenient().when(mediaRepository.findById(any())).thenAnswer(invocation -> {
            Media media = new Media();
            media.setMediaId(invocation.getArgument(0));
            media.setBucket(BUCKET);
            return Optional.of(media);
        });

        lenient().when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presigned);
    }

    private void stubPresigner() {
        lenient().when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presigned);
    }

    // Montado fora de qualquer when(...): stubbing aninhado leave o Mockito com
    // stubbing inacabado e derruba todos os testes da classe.
    private PresignedGetObjectRequest presignedWithUrl(String url) {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        try {
            lenient().when(presigned.url()).thenReturn(new URL(url));
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        return presigned;
    }

    private DeleteObjectRequest deletedUploadedObject() {
        return argThat(request -> BUCKET.equals(request.bucket())
                && request.key() != null
                && request.key().startsWith(FOLDER + "/")
                && request.key().endsWith(".png"));
    }

    private void completeTransaction(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(status));
    }

    private MockMultipartFile file(String originalFilename) {
        return new MockMultipartFile("icon", originalFilename, "image/png", new byte[10]);
    }
}
