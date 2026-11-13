package com.motadev.clone_reddit.media.validator;

import com.motadev.clone_reddit.media.config.MediaTypeRule;
import com.motadev.clone_reddit.media.config.MediaUploadProperties;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaFileValidatorTest {

    private static final long MAX_SIZE_BYTES = 1024L;

    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00
    };
    private static final byte[] GIF_HEADER = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00};
    private static final byte[] WEBP_HEADER = {
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50
    };

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
    void deveAceitarJpegComMagicBytesCorretos() {
        assertThat(validator.validateAndResolveExtension(file("foto.jpg", "image/jpeg", JPEG_HEADER)))
                .isEqualTo(".jpg");
    }

    @Test
    void deveAceitarPngComMagicBytesCorretos() {
        assertThat(validator.validateAndResolveExtension(file("icon.png", "image/png", PNG_HEADER)))
                .isEqualTo(".png");
    }

    @Test
    void deveAceitarWebpComMagicBytesCorretos() {
        assertThat(validator.validateAndResolveExtension(file("banner.webp", "image/webp", WEBP_HEADER)))
                .isEqualTo(".webp");
    }

    @Test
    void deveAceitarGifComMagicBytesCorretos() {
        assertThat(validator.validateAndResolveExtension(file("anim.gif", "image/gif", GIF_HEADER)))
                .isEqualTo(".gif");
    }

    @Test
    void deveNormalizarExtensaoEmMaiusculas() {
        assertThat(validator.validateAndResolveExtension(file("BANNER.PNG", "image/png", PNG_HEADER)))
                .isEqualTo(".png");
    }

    @Test
    void deveUsarUltimoPontoComoSeparadorDaExtensao() {
        assertThat(validator.validateAndResolveExtension(file("icone.versao.2.jpeg", "image/jpeg", JPEG_HEADER)))
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

        assertThat(permissive.validateAndResolveExtension(file("foto.jpg", "image/jpg", JPEG_HEADER)))
                .isEqualTo(".jpg");
    }

    @Test
    void deveRejeitarCombinacaoInconsistenteEntreContentTypeEExtensao() {
        // O arquivo e .jpg mas foi declarado como image/png. Com duas listas soltas
        // de allowlist isso passaria; a regra pareada exige que content-type e
        // extensao venham da mesma regra.
        assertThatThrownBy(() -> validator.validateAndResolveExtension(file("foto.jpg", "image/png", PNG_HEADER)))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("not allowed for content type image/png");
    }

    @Test
    void deveRejeitarQuandoMagicBytesNaoBatamComContentTypeDeclarado() {
        assertThatThrownBy(() -> validator.validateAndResolveExtension(file("foto.png", "image/png", JPEG_HEADER)))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("does not match the declared content type");
    }

    @Test
    void deveRejeitarQuandoMagicBytesForemDesconhecidos() {
        assertThatThrownBy(() -> validator.validateAndResolveExtension(
                file("icon.png", "image/png", new byte[]{0x00, 0x01, 0x02, 0x03})))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("does not match the declared content type");
    }

    @Test
    void deveRejeitarQuandoConteudoForCurtoDemaisParaAssinatura() {
        assertThatThrownBy(() -> validator.validateAndResolveExtension(
                file("icon.png", "image/png", new byte[]{(byte) 0x89, 0x50})))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("does not match the declared content type");
    }

    @Test
    void deveRejeitarArquivoAcimaDoLimiteDeTamanho() {
        assertThatThrownBy(() -> validator.validateAndResolveExtension(
                file("icon.png", "image/png", padded(PNG_HEADER, (int) MAX_SIZE_BYTES + 1))))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("maximum allowed size");
    }

    @Test
    void deveAceitarArquivoExatamenteNoLimiteDeTamanho() {
        assertThat(validator.validateAndResolveExtension(
                file("icon.png", "image/png", padded(PNG_HEADER, (int) MAX_SIZE_BYTES))))
                .isEqualTo(".png");
    }

    @Test
    void deveRejeitarContentTypeNaoPermitido() {
        assertThatThrownBy(() -> validator.validateAndResolveExtension(file("icon.png", "application/pdf", PNG_HEADER)))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("content type");
    }

    @Test
    void deveRejeitarContentTypeNulo() {
        MockMultipartFile file = new MockMultipartFile("file", "icon.png", null, PNG_HEADER);

        assertThatThrownBy(() -> validator.validateAndResolveExtension(file))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("content type");
    }

    @Test
    void deveRejeitarExtensaoNaoPermitida() {
        assertThatThrownBy(() -> validator.validateAndResolveExtension(file("malware.exe", "image/png", PNG_HEADER)))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("extension");
    }

    @Test
    void deveRejeitarArquivoSemExtensao() {
        assertThatThrownBy(() -> validator.validateAndResolveExtension(file("icon", "image/png", PNG_HEADER)))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("extension");
    }

    @Test
    void deveRejeitarFilenameNulo() {
        MockMultipartFile file = new MockMultipartFile("file", null, "image/png", PNG_HEADER);

        assertThatThrownBy(() -> validator.validateAndResolveExtension(file))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("extension");
    }

    @Test
    void deveRejeitarExtensaoComBarraParaImpedirTravessiaDeDiretorio() {
        // A objectKey e folder + "/" + uuid + extensao, e o Spring nao sanitiza o
        // filename (StandardMultipartFile devolve o valor cru do Content-Disposition).
        // A allowlist e a sanitizacao: "/" nunca casa, entao a chave nao escapa da pasta.
        assertThatThrownBy(() -> validator.validateAndResolveExtension(
                file("icon.png/../../../evil", "image/png", PNG_HEADER)))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessageContaining("extension");
    }

    private MockMultipartFile file(String originalFilename, String contentType, byte[] content) {
        return new MockMultipartFile("file", originalFilename, contentType, content);
    }

    private static byte[] padded(byte[] header, int size) {
        byte[] content = Arrays.copyOf(header, size);
        return content;
    }
}
