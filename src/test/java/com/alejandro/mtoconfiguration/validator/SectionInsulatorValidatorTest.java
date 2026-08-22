package com.alejandro.mtoconfiguration.validator;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.infrastructure.SectionInsulatorValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoErrors;

class SectionInsulatorValidatorTest {

    private final SectionInsulatorValidator validator = new SectionInsulatorValidator();

    @Test
    void aceptaUnAisladorValido() {
        assertNoErrors(validator.validateBeforeSave(ValidDtos.rootSectionInsulator()));
    }

    @Test
    void exigeLosCamposPropios() {
        List<Alert> alerts = validator.validateBeforeSave(new SectionInsulatorDTO());

        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "name");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "enabled");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "stationId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("un nombre en blanco no es un nombre, aunque no sea nulo")
    void rechazaNombreEnBlanco(String name) {
        SectionInsulatorDTO dto = ValidDtos.rootSectionInsulator();
        dto.setName(name);

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_REQUIRED_FIELD, "name");
    }

    @Test
    @DisplayName("el nombre admite hasta 200 caracteres, la longitud real de la columna")
    void aplicaLaLongitudDeLaColumna() {
        SectionInsulatorDTO enElLimite = ValidDtos.rootSectionInsulator();
        enElLimite.setName(ValidDtos.text(200));
        assertNoErrors(validator.validateBeforeSave(enElLimite));

        SectionInsulatorDTO pasado = ValidDtos.rootSectionInsulator();
        pasado.setName(ValidDtos.text(201));
        assertError(validator.validateBeforeSave(pasado), ErrorCodes.VALIDATION_OUT_OF_RANGE, "name");
    }

    @Test
    void noExigeStationIdComoHijo() {
        assertNoErrors(validator.validateBeforeSaveAsChild(ValidDtos.newSectionInsulator()));
    }
}
