package com.motadev.clone_reddit.media.validator;

import com.motadev.clone_reddit.media.config.MediaTypeRule;
import com.motadev.clone_reddit.media.config.MediaUploadProperties;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaFileValidatorTest {

    private static final long MAX_SIZE_BYTES = 1024L;

    private MediaFileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MediaFileValidator(properties());
    }

    private static MediaUploadProperties properties() {
        return new MediaUploadProperties(MAX_SIZE_BYTES, List.of(
                new MediaTypeRule("image/jpeg", Set.of(".jpg", ".jpeg")),
                new MediaTypeRule("image/png", Set.of(".png")),
                new MediaTypeRule("image/webp", Set.of(".webp")),
                new MediaTypeRule("image/gif", Set.of(".gif"))
        ));
    }

    @Test
    void deveAceitarArquivoValidoERetornarExtensaoNormalizada() {
        assertThat(validator.validateAndResolveExtension(file("icon.png", "image/png", 10)))
                .isEqualTo(".png");
    }

    @Test
    void deveNormalizarExtensaoEmMaiusculas() {
        assertThat(validator.validateAndResolveExtension(file("BANNER.PNG", "image/png", 10)))
                .isEqualTo(".png");
    }

    @Test
    void deveUsarUltimoPontoComoSeparadorDaExtensao() {
        assertThat(validator.validateAndResolveExtension(file("icone.versao.2.jpeg", "image/jpeg", 10)))
                .isEqualTo(".jpeg");
    }

    @Test
    void deveAceitarContentTypeNaoPadraoQueCompartilhaExtensao() {
        // Navegadores do mundo real declaram image/jpg (nao-padrao) junto de um
        // arquivo .jpg. A checagem e permissiva de proposito para nao rejeitar
        // upload legitimo.
        MediaFileValidator permissive = new MediaFileValidator(new MediaUploadProperties(MAX_SIZE_BYTES, List.of(
                new MediaTypeRule("image/jpeg", Set.of(".jpg", ".jpeg")),
                new MediaTypeRule("image/jpg", Set.of(".jpg", ".jpeg"))
        )));

        assertThat(permissive.validateAndResolveExtension(file("foto.jpg", "image/jpg", 10)))
                .isEqualTo(".jpg");
    }

    @Test
    void deveRejeitarCombinacaoInconsistenteEntreContentTypeEExtensao() {
        // O arquivo e .jpg mas foi declarado como image/png. Com duas listas soltas
        // de allowlist isso passaria; a regra pareada exige que content-type e
        // extensao venham da mesma regra.
        assertThatThrownBy(() -> validator.validateAndResolveExtension(file("foto.jpg", "image/png", 10)))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("not allowed for content type image/png");
    }

    @Test
    void deveRejeitarArquivoAcimaDoLimiteDeTamanho() {
        assertThatThrownBy(() -> validator.validateAndResolveExtension(file("icon.png", "image/png", MAX_SIZE_BYTES + 1)))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("maximum allowed size");
    }

    @Test
    void deveAceitarArquivoExatamenteNoLimiteDeTamanho() {
        assertThat(validator.validateAndResolveExtension(file("icon.png", "image/png", MAX_SIZE_BYTES)))
                .isEqualTo(".png");
    }

    @Test
    void deveRejeitarContentTypeNaoPermitido() {
        assertThatThrownBy(() -> validator.validateAndResolveExtension(file("icon.png", "application/pdf", 10)))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("content type");
    }

    @Test
    void deveRejeitarContentTypeNulo() {
        MockMultipartFile file = new MockMultipartFile("file", "icon.png", null, new byte[10]);

        assertThatThrownBy(() -> validator.validateAndResolveExtension(file))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("content type");
    }

    @Test
    void deveRejeitarExtensaoNaoPermitida() {
        assertThatThrownBy(() -> validator.validateAndResolveExtension(file("malware.exe", "image/png", 10)))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("extension");
    }

    @Test
    void deveRejeitarArquivoSemExtensao() {
        assertThatThrownBy(() -> validator.validateAndResolveExtension(file("icon", "image/png", 10)))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("extension");
    }

    @Test
    void deveRejeitarFilenameNulo() {
        MockMultipartFile file = new MockMultipartFile("file", null, "image/png", new byte[10]);

        assertThatThrownBy(() -> validator.validateAndResolveExtension(file))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("extension");
    }

    @Test
    void deveRejeitarExtensaoComBarraParaImpedirTravessiaDeDiretorio() {
        // A objectKey e folder + "/" + uuid + extensao, e o Spring nao sanitiza o
        // filename (StandardMultipartFile devolve o valor cru do Content-Disposition).
        // A allowlist e a sanitizacao: "/" nunca casa, entao a chave nao escapa da pasta.
        assertThatThrownBy(() -> validator.validateAndResolveExtension(file("icon.png/../../../evil", "image/png", 10)))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("extension");
    }

    private MockMultipartFile file(String originalFilename, String contentType, long size) {
        return new MockMultipartFile("file", originalFilename, contentType, new byte[(int) size]);
    }
}
