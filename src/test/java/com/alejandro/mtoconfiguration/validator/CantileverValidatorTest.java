package com.alejandro.mtoconfiguration.validator;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.infrastructure.CantileverValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.SteadyArmValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoErrors;

class CantileverValidatorTest {

    private final CantileverValidator validator = new CantileverValidator(new SteadyArmValidator());

    @Test
    void aceptaUnaCantileverValida() {
        assertNoErrors(validator.validateBeforeSave(ValidDtos.rootCantilever()));
    }

    @Test
    @DisplayName("solo el tipo de mensula es obligatorio")
    void exigeElTipoDeMensula() {
        List<Alert> alerts = validator.validateBeforeSave(new CantileverDTO());

        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "cantileverType");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "profileId");
    }

    @Test
    @DisplayName("las seis magnitudes son opcionales: en el origen casi nunca vienen todas")
    void noExigeLasMagnitudes() {
        // De las 14.592 mensulas de los workbooks, cwElevation viene en el 17 % y
        // windDeflection practicamente en ninguna. Exigirlas dejaba fuera el catalogo
        // entero, y rellenarlas con ceros mete medidas inventadas.
        List<Alert> alerts = validator.validateBeforeSave(new CantileverDTO());

        for (String campo : List.of("cwHeight", "stagger", "catenaryHeight",
                                    "cwElevation", "windDeflection", "armAngle")) {
            assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, campo);
        }
    }

    @Test
    @DisplayName("una mensula con solo el tipo es valida")
    void aceptaUnaMensulaSinMagnitudes() {
        CantileverDTO dto = new CantileverDTO();
        dto.setCantileverType(ValidDtos.cantileverType());
        dto.setProfileId(1L);

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    @ParameterizedTest
    @CsvSource({
            // Un dígito entero y tres decimales: NUMERIC(4,3)
            "cwHeight,      12.500",
            "cwHeight,      1.2345",
            "catenaryHeight,12.500",
            "cwElevation,   12.500",
            "windDeflection,12.500",
            // Tres dígitos enteros y ninguno decimal: NUMERIC(3,0)
            "stagger,       1000",
            "stagger,       10.5",
            // Dos dígitos enteros y tres decimales: NUMERIC(5,3)
            "armAngle,      100.000",
            "armAngle,      12.3456"
    })
    @DisplayName("la precisión que se acepta es la de la columna, no una genérica de 10 dígitos")
    void rechazaPrecisionQueLaColumnaNoAdmite(String field, String value) {
        CantileverDTO dto = ValidDtos.rootCantilever();
        setField(dto, field, new BigDecimal(value));

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_OUT_OF_RANGE, field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-90.001", "90.001", "-91", "91"})
    @DisplayName("el ángulo del brazo vive en [-90, 90]; antes solo lo comprobaba Hibernate")
    void rechazaAnguloFueraDeRango(String value) {
        CantileverDTO dto = ValidDtos.rootCantilever();
        dto.setArmAngle(new BigDecimal(value));

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_OUT_OF_RANGE, "armAngle");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-90.000", "0.000", "90.000", "-45.500"})
    void aceptaAnguloDentroDeRango(String value) {
        CantileverDTO dto = ValidDtos.rootCantilever();
        dto.setArmAngle(new BigDecimal(value));

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    @Test
    @DisplayName("la altura del hilo de contacto no puede ser negativa: @DecimalMin(0.000)")
    void rechazaAlturaNegativa() {
        CantileverDTO dto = ValidDtos.rootCantilever();
        dto.setCwHeight(new BigDecimal("-1.000"));

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_OUT_OF_RANGE, "cwHeight");
    }

    @Test
    @DisplayName("los errores de la ménsula llegan con la ruta anidada")
    void propagaLosErroresDeLaMensulaConSuRuta() {
        SteadyArmDTO steadyArm = ValidDtos.existingSteadyArm();
        steadyArm.setSteadyArmType(null);

        CantileverDTO dto = ValidDtos.rootCantilever();
        dto.setSteadyArm(steadyArm);

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_REQUIRED_FIELD,
                "steadyArm.steadyArmType");
    }

    @Test
    @DisplayName("una ménsula nueva es un alta válida: SteadyArm cascadea desde Cantilever")
    void aceptaMensulaNuevaAnidada() {
        CantileverDTO dto = ValidDtos.rootCantilever();
        dto.setSteadyArm(ValidDtos.newSteadyArm());

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    @Test
    @DisplayName("la ménsula sin brazo es válida: 4.816 del origen no traen ninguno")
    void aceptaMensulaSinBrazo() {
        CantileverDTO dto = ValidDtos.rootCantilever();
        dto.setSteadyArm(null);

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    @Test
    @DisplayName("los argumentos del mensaje no se prefijan como si fueran rutas")
    void noPrefijaLosArgumentosDelMensaje() {
        SteadyArmDTO steadyArm = ValidDtos.existingSteadyArm();
        steadyArm.setLength(9_999L);

        CantileverDTO dto = ValidDtos.rootCantilever();
        dto.setSteadyArm(steadyArm);

        Alert alert = validator.validateBeforeSave(dto).stream()
                .filter(a -> ErrorCodes.VALIDATION_OUT_OF_RANGE.equals(a.getMessage()))
                .findFirst()
                .orElseThrow();

        org.assertj.core.api.Assertions.assertThat(alert.getFields())
                .containsExactly("steadyArm.length", "1", "2000");
    }

    private static void setField(CantileverDTO dto, String field, BigDecimal value) {
        switch (field) {
            case "cwHeight" -> dto.setCwHeight(value);
            case "stagger" -> dto.setStagger(value);
            case "catenaryHeight" -> dto.setCatenaryHeight(value);
            case "cwElevation" -> dto.setCwElevation(value);
            case "windDeflection" -> dto.setWindDeflection(value);
            case "armAngle" -> dto.setArmAngle(value);
            default -> throw new IllegalArgumentException("Campo desconocido: " + field);
        }
    }
}
