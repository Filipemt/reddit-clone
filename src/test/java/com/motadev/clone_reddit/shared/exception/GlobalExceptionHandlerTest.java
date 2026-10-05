package com.motadev.clone_reddit.shared.exception;

import com.motadev.clone_reddit.shared.exception.dtos.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/communities");
    }

    @Test
    void deveResponder413QuandoOUploadExcedeOLimiteDaServlet() {
        // O advice nao estende ResponseEntityExceptionHandler, entao sem o handler
        // explicito a excecao cai no catch-all e o cliente receberia 500.
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(5L * 1024 * 1024);

        ResponseEntity<ApiError> response = handler.handleMaxUploadSizeExceeded(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().status()).isEqualTo(413);
        assertThat(response.getBody().message()).contains("maximum allowed size");
        assertThat(response.getBody().path()).isEqualTo("/communities");
    }

    @Test
    void deveResponder422QuandoOArquivoViolaAsRegrasDeUpload() {
        ResourceInvalidException ex = new ResourceInvalidException("File extension is not allowed.");

        ResponseEntity<ApiError> response = handler.handleResourceInvalid(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().message()).isEqualTo("File extension is not allowed.");
    }

    @Test
    void deveResponder500ParaExcecaoDesconhecida() {
        ResponseEntity<ApiError> response = handler.handleGeneric(new IllegalStateException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
    }
}
