package com.alejandro.mtoconfiguration.validator;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.infrastructure.SteadyArmValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertAllDanger;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoErrors;

class SteadyArmValidatorTest {

    private final SteadyArmValidator validator = new SteadyArmValidator();

    @Test
    void aceptaUnaMensulaValida() {
        assertNoErrors(validator.validateBeforeSave(ValidDtos.existingSteadyArm()));
    }

    @Test
    void exigeLosCamposPropios() {
        List<Alert> alerts = validator.validateBeforeSave(new SteadyArmDTO());

        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "length");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "steadyArmType");
        assertAllDanger(alerts);
    }

    @Test
    @DisplayName("el DTO nulo se señala con el nombre de la entidad, sin NPE")
    void rechazaElDtoNulo() {
        assertError(validator.validateBeforeSave(null), ErrorCodes.VALIDATION_REQUIRED_FIELD, "steadyArm");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 2_001L, 999_999L})
    @DisplayName("la longitud fuera de [1, 2000] se rechaza: la columna declara @Max(2000)")
    void rechazaLongitudFueraDeRango(long length) {
        SteadyArmDTO dto = ValidDtos.existingSteadyArm();
        dto.setLength(length);

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_OUT_OF_RANGE, "length");
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 1_000L, 2_000L})
    void aceptaLongitudDentroDeRango(long length) {
        SteadyArmDTO dto = ValidDtos.existingSteadyArm();
        dto.setLength(length);

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    @Test
    @DisplayName("en alta raíz el cantileverId es obligatorio")
    void exigeCantileverIdEnRaiz() {
        SteadyArmDTO dto = ValidDtos.newSteadyArm();

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_REQUIRED_FIELD, "cantileverId");
    }

    @Test
    @DisplayName("anidada bajo su cantilever no se exige el cantileverId: lo pone el mapper")
    void noExigeCantileverIdComoHija() {
        assertNoErrors(validator.validateBeforeSaveAsChild(ValidDtos.newSteadyArm()));
    }

    @Test
    void exigeElIdAlActualizar() {
        SteadyArmDTO dto = ValidDtos.existingSteadyArm();
        dto.setId(null);

        assertError(validator.validateBeforeUpdate(dto), ErrorCodes.VALIDATION_REQUIRED_FIELD, "id");
    }

    @Test
    void exigeElIdAlBorrar() {
        assertError(validator.validateBeforeDelete(new SteadyArmDTO()), ErrorCodes.VALIDATION_REQUIRED_FIELD, "id");
        assertNoErrors(validator.validateBeforeDelete(ValidDtos.existingSteadyArm()));
    }

    @Test
    @DisplayName("borrar no arrastra las reglas de alta: basta con el id")
    void borrarSoloComprubaElId() {
        SteadyArmDTO dto = new SteadyArmDTO();
        dto.setId(10L);

        assertNoErrors(validator.validateBeforeDelete(dto));
        assertNoError(validator.validateBeforeDelete(dto), ErrorCodes.VALIDATION_REQUIRED_FIELD, "length");
    }
}
