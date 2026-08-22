package com.alejandro.mtoconfiguration.core.exception;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class BaseExceptionTest {

    /**
     * El constructor reasignaba el parámetro en vez del campo, así que el campo quedaba nulo y
     * {@code getErrors()} lanzaba NPE. Como es el constructor de {@link ValidationException},
     * cualquier validación fallida acababa en 500 sin llegar a devolver el detalle.
     */
    @Test
    @DisplayName("las alertas del constructor de lista se conservan y no lanzan NPE")
    void conservaLasAlertasDeLaLista() {
        List<Alert> alerts = List.of(
                Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, "name"),
                Alert.ofDanger(ErrorCodes.VALIDATION_OUT_OF_RANGE, "kp", "1", "200"));

        BaseException exception = new ValidationException(alerts);

        assertThatCode(exception::getErrors).doesNotThrowAnyException();
        assertThat(exception.getErrors()).hasSize(2);
    }

    @Test
    @DisplayName("dos campos con el mismo código son dos errores distintos")
    void noColapsaErroresDelMismoCodigoEnCamposDistintos() {
        BaseException exception = new ValidationException(List.of(
                Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, "name"),
                Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, "enabled")));

        assertThat(exception.getErrors()).hasSize(2);
    }

    @Test
    @DisplayName("la alerta repetida exactamente sí se colapsa")
    void colapsaLaAlertaExactamenteRepetida() {
        BaseException exception = new ValidationException(List.of(
                Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, "name"),
                Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, "name")));

        assertThat(exception.getErrors()).hasSize(1);
    }

    @Test
    void toleraListasVaciasYNulas() {
        assertThat(new ValidationException(List.<Alert>of()).getErrors()).isEmpty();
        assertThatCode(() -> new ValidationException((List<Alert>) null).getErrors())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el mensaje de la excepción queda disponible para el log")
    void exponeElMensajeParaElLog() {
        assertThat(new ValidationException(List.of(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, "name")))
                .getMessage())
                .isEqualTo(ErrorCodes.VALIDATION_REQUIRED_FIELD);

        assertThat(new BaseException("algo falló").getMessage()).isEqualTo("algo falló");
    }

    @Test
    void lasAlertasDevueltasSonInmutables() {
        BaseException exception = new BaseException("boom");

        assertThatCode(() -> exception.getErrors().add(Alert.ofDanger("otro")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
