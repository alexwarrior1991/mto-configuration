package com.alejandro.mtoconfiguration.validator;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.infrastructure.DisconnectorValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoErrors;

class DisconnectorValidatorTest {

    private final DisconnectorValidator validator = new DisconnectorValidator();

    @Test
    void aceptaUnSeccionadorValido() {
        assertNoErrors(validator.validateBeforeSave(ValidDtos.rootDisconnector()));
    }

    @Test
    void exigeLosCamposPropiosYLasClavesAjenas() {
        List<Alert> alerts = validator.validateBeforeSave(new DisconnectorDTO());

        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "name");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "onLoad");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "disconnectorFunction");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "stationId");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "profileId");
    }

    @Test
    @DisplayName("una LOV sin id ni código no sirve para resolver la referencia")
    void rechazaLovVacia() {
        DisconnectorDTO dto = ValidDtos.rootDisconnector();
        dto.setDisconnectorFunction(new DisconnectorFunctionDTO());

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_REQUIRED_FIELD, "disconnectorFunction");
    }

    @Test
    @DisplayName("una LOV solo con código es válida: se resuelve por código")
    void aceptaLovSoloConCodigo() {
        DisconnectorFunctionDTO lov = new DisconnectorFunctionDTO();
        lov.setCode("FN");

        DisconnectorDTO dto = ValidDtos.rootDisconnector();
        dto.setDisconnectorFunction(lov);

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    @Test
    void noExigeLasClavesAjenasComoHijo() {
        assertNoErrors(validator.validateBeforeSaveAsChild(ValidDtos.newDisconnector()));
    }
}
