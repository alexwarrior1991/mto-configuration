package com.alejandro.mtoconfiguration.core.exception;

import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorDetail;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.StandardErrorCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCatalogTest {

    private final ErrorCatalog catalog = new ErrorCatalog();

    @ParameterizedTest
    @CsvSource({
            "VAL-000, 400",
            "VAL-001, 400",
            "VAL-003, 400",
            "BUS-001, 422",
            "BUS-002, 409",
            "NOT-001, 404",
            "SEC-001, 401",
            "SEC-002, 403",
            "SEC-003, 401",
            "CON-001, 409",
            "INT-001, 504",
            "INT-002, 502",
            "TEC-001, 500",
            "TEC-999, 500"
    })
    @DisplayName("cada código se traduce al estado HTTP que le corresponde")
    void mapeaCodigoAEstado(String code, int expected) {
        assertThat(catalog.statusOf(code).value()).isEqualTo(expected);
    }

    @Test
    @DisplayName("un código desconocido no revienta: degrada a 500")
    void codigoDesconocidoDegradaA500() {
        assertThat(catalog.statusOf("NO-EXISTE")).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(catalog.statusOf(null)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ParameterizedTest
    @EnumSource(StandardErrorCodes.class)
    @DisplayName("todo el catálogo tiene estado y título; ninguna familia se queda sin mapear")
    void todoElCatalogoEstaMapeado(StandardErrorCodes entry) {
        assertThat(catalog.statusOf(entry.code())).isNotNull();
        assertThat(catalog.titleOf(entry.code())).isNotBlank();
    }

    @Test
    @DisplayName("la alerta se convierte en detalle: campo, código y mensaje formateado")
    void convierteAlertaEnDetalle() {
        ApiErrorDetail detail = catalog.toDetail(
                Alert.ofDanger(ErrorCodes.VALIDATION_OUT_OF_RANGE, "kp", "1", "200"));

        assertThat(detail.field()).isEqualTo("kp");
        assertThat(detail.code()).isEqualTo(ErrorCodes.VALIDATION_OUT_OF_RANGE);
        assertThat(detail.message()).contains("kp").contains("1").contains("200");
    }

    @Test
    @DisplayName("una alerta sin campos no tiene ruta, pero sí mensaje")
    void toleraAlertaSinCampos() {
        ApiErrorDetail detail = catalog.toDetail(Alert.ofDanger(ErrorCodes.UNEXPECTED_ERROR));

        assertThat(detail.field()).isNull();
        assertThat(detail.message()).isNotBlank();
    }

    /**
     * Un catálogo mal configurado no puede tumbar la respuesta de error: es justo lo que el cliente
     * necesita para entender el fallo.
     */
    @Test
    @DisplayName("una plantilla que no case con sus argumentos degrada, no lanza")
    void plantillaConArgumentosInsuficientesDegrada() {
        String message = catalog.resolveMessage(ErrorCodes.VALIDATION_OUT_OF_RANGE, List.of());

        assertThat(message).isNotBlank();
    }

    @Test
    void codigoFueraDelCatalogoDevuelveElPropioCodigo() {
        assertThat(catalog.resolveMessage("XXX-999", List.of())).isEqualTo("XXX-999");
    }

    @Test
    @DisplayName("solo se marca reintentable lo que de verdad puede funcionar al reintentar")
    void reintentable() {
        assertThat(catalog.isRetryable(ErrorCodes.CONCURRENCY_CONFLICT)).isTrue();
        assertThat(catalog.isRetryable(ErrorCodes.INTEGRATION_TIMEOUT)).isTrue();
        assertThat(catalog.isRetryable(ErrorCodes.VALIDATION_REQUIRED_FIELD)).isFalse();
        assertThat(catalog.isRetryable("NO-EXISTE")).isFalse();
    }
}
