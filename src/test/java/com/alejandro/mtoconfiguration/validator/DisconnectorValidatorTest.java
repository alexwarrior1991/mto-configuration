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
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoError;
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
    }

    @Test
    @DisplayName("el poste, el estado normal y el accionamiento son opcionales: hay seccionadores que no están en un poste")
    void noExigeElPosteNiElEstadoNormalNiElAccionamiento() {
        DisconnectorDTO dto = ValidDtos.rootDisconnector();
        dto.setProfileId(null);

        assertNoErrors(validator.validateBeforeSave(dto));

        List<Alert> alerts = validator.validateBeforeSave(new DisconnectorDTO());
        assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "profileId");
        assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "normallyOpen");
        assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "driveType");
    }

    @Test
    @DisplayName("el KP y la vía propios son solo de un seccionador sin poste: en uno en un poste son los del perfil")
    void kpYViaSoloSinPoste() {
        DisconnectorDTO sinPoste = ValidDtos.rootDisconnector();
        sinPoste.setProfileId(null);
        sinPoste.setKp("98375.5");
        sinPoste.setTrackId(3L);
        assertNoErrors(validator.validateBeforeSave(sinPoste));

        DisconnectorDTO enPoste = ValidDtos.rootDisconnector();
        enPoste.setKp("98375.5");
        enPoste.setTrackId(3L);
        List<Alert> alerts = validator.validateBeforeSave(enPoste);
        assertError(alerts, ErrorCodes.BUSINESS_RULE_VIOLATION, "kp");
        assertError(alerts, ErrorCodes.BUSINESS_RULE_VIOLATION, "trackId");
    }

    @Test
    @DisplayName("el KP es un número en metros, con punto decimal y la precisión de la columna")
    void kpConFormato() {
        DisconnectorDTO dto = ValidDtos.rootDisconnector();
        dto.setProfileId(null);

        dto.setKp("98+375");
        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_INVALID_FORMAT, "kp");
        dto.setKp("98375.1234");
        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_OUT_OF_RANGE, "kp");
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
