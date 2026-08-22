package com.alejandro.mtoconfiguration.utils;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ValidatorUtilsTest {

    private static final String CODE = ErrorCodes.VALIDATION_OUT_OF_RANGE;
    private static final String FIELD = "campo";

    private final List<Alert> alerts = new ArrayList<>();

    private ValidatorUtils check() {
        return ValidatorUtils.of(alerts);
    }

    @Test
    @DisplayName("of es estático: no hace falta una instancia para crear otra")
    void ofEsEstatico() {
        assertThat(ValidatorUtils.of(alerts).getAlerts()).isSameAs(alerts);
    }

    @Nested
    class Obligatoriedad {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   ", "\t\n"})
        void validateRequiredStringRechazaNuloYEnBlanco(String value) {
            check().validateRequiredString(value, ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD);

            assertThat(alerts).hasSize(1);
        }

        @Test
        void validateRequiredStringAceptaTextoConContenido() {
            check().validateRequiredString(" hola ", ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD);

            assertThat(alerts).isEmpty();
        }

        @Test
        @DisplayName("validateRequiredField solo mira el nulo: la cadena vacía la filtra validateRequiredString")
        void validateRequiredFieldSoloMiraElNulo() {
            check().validateRequiredField("", ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD);

            assertThat(alerts).isEmpty();
        }

        @Test
        void validateRequiredLovDtoAceptaIdOCodigo() {
            LovDTO porId = new LovDTO();
            porId.setId(1L);

            LovDTO porCodigo = new LovDTO();
            porCodigo.setCode("X");

            check().validateRequiredLovDTO(porId, CODE, FIELD)
                    .validateRequiredLovDTO(porCodigo, CODE, FIELD)
                    .validateRequiredLovDTO(new LovDTO(), CODE, FIELD)
                    .validateRequiredLovDTO(null, CODE, FIELD);

            assertThat(alerts).hasSize(2);
        }

        @Test
        @DisplayName("un código en blanco no resuelve una LOV")
        void validateRequiredLovCodeRechazaCodigoEnBlanco() {
            LovDTO lov = new LovDTO();
            lov.setCode("   ");

            check().validateRequiredLovCode(lov, CODE, FIELD);

            assertThat(alerts).hasSize(1);
        }
    }

    @Nested
    class Rangos {

        @Test
        @DisplayName("un límite nulo significa 'sin límite por ese lado', no una excepción")
        void validateRangeDeBigDecimalToleraLimitesNulos() {
            assertThatCode(() -> check()
                    .validateRange(BigDecimal.ONE, null, null, CODE, FIELD)
                    .validateRange(BigDecimal.ONE, BigDecimal.ZERO, null, CODE, FIELD)
                    .validateRange(BigDecimal.ONE, null, BigDecimal.TEN, CODE, FIELD))
                    .doesNotThrowAnyException();

            assertThat(alerts).isEmpty();
        }

        @Test
        void validateRangeDeBigDecimalAplicaElLimiteQueExiste() {
            check().validateRange(new BigDecimal("-1"), BigDecimal.ZERO, null, CODE, FIELD);

            assertThat(alerts).hasSize(1);
        }

        @Test
        @DisplayName("el valor nulo nunca genera alerta de rango: eso lo dice validateRequiredField")
        void validateRangeIgnoraElNulo() {
            check().validateRange((BigDecimal) null, BigDecimal.ZERO, BigDecimal.TEN, CODE, FIELD)
                    .validateRange((Integer) null, 0, 10, CODE, FIELD)
                    .validateRange((Long) null, 0L, 10L, CODE, FIELD)
                    .validateRange((Double) null, 0d, 10d, CODE, FIELD);

            assertThat(alerts).isEmpty();
        }

        @Test
        void validateRangeReportaElMinimoYElMaximoComoArgumentos() {
            check().validateRange(50, 1, 10, CODE, FIELD);

            assertThat(alerts.get(0).getFields()).containsExactly(FIELD, "1", "10");
        }
    }

    @Nested
    class Precision {

        @Test
        void validateBigDecimalWithPrecisionAplicaEnterosYDecimales() {
            check().validateBigDecimalWithPrecision(new BigDecimal("1.234"), 1, 3, CODE, FIELD)
                    .validateBigDecimalWithPrecision(new BigDecimal("0.5"), 1, 3, CODE, FIELD)
                    .validateBigDecimalWithPrecision(new BigDecimal("-9.999"), 1, 3, CODE, FIELD);

            assertThat(alerts).isEmpty();

            check().validateBigDecimalWithPrecision(new BigDecimal("12.3"), 1, 3, CODE, FIELD)
                    .validateBigDecimalWithPrecision(new BigDecimal("1.2345"), 1, 3, CODE, FIELD);

            assertThat(alerts).hasSize(2);
        }

        @Test
        @DisplayName("el signo no cuenta como dígito entero")
        void validateBigDecimalWithSplitIgnoraElSigno() {
            check().validateBigDecimalWithSplit(new BigDecimal("-999"), 3, 0, CODE, FIELD);

            assertThat(alerts).isEmpty();
        }
    }

    @Nested
    class FormatoYTamano {

        @ParameterizedTest
        @ValueSource(strings = {"1", "-1", "1.5", "-1.5", "0"})
        void validateNumericFieldAceptaSignoYDecimales(String value) {
            check().validateNumericField(value, CODE, FIELD);

            assertThat(alerts).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"1,5", "abc", "1.2.3", ""})
        void validateNumericFieldRechazaLoQueNoEsNumero(String value) {
            check().validateNumericField(value, CODE, FIELD);

            assertThat(alerts).hasSize(1);
        }

        @Test
        void validateNumericFieldAceptaCualquierNumero() {
            check().validateNumericField(42, CODE, FIELD)
                    .validateNumericField(new BigDecimal("1.5"), CODE, FIELD);

            assertThat(alerts).isEmpty();
        }

        @Test
        void validateMaxSizeCuentaLosElementos() {
            check().validateMaxSize(null, 2, CODE, FIELD)
                    .validateMaxSize(List.of(), 2, CODE, FIELD)
                    .validateMaxSize(List.of(1, 2), 2, CODE, FIELD);

            assertThat(alerts).isEmpty();

            check().validateMaxSize(List.of(1, 2, 3), 2, CODE, FIELD);

            assertThat(alerts).hasSize(1);
        }
    }

    @Nested
    class DigitoDeControlDeContenedor {

        @Test
        void aceptaUnCodigoValido() {
            check().validateContainerControlDigit("CSQU3054383", CODE, FIELD);

            assertThat(alerts).isEmpty();
        }

        @Test
        void rechazaUnDigitoDeControlIncorrecto() {
            check().validateContainerControlDigit("CSQU3054384", CODE, FIELD);

            assertThat(alerts).hasSize(1);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "CORTO", "CSQU305438333", "1234567890A", "CSQU30543 8", "csqu3054383"})
        @DisplayName("una entrada malformada da alerta, no una excepción")
        void noRevientaConEntradasMalformadas(String value) {
            assertThatCode(() -> check().validateContainerControlDigit(value, CODE, FIELD))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class Igualdad {

        @Test
        @DisplayName("comparar con un nulo no es una desigualdad: la obligatoriedad se declara aparte")
        void validateBigDecimalEqualityIgnoraLosNulos() {
            check().validateBigDecimalEquality(null, BigDecimal.ONE, CODE, FIELD)
                    .validateBigDecimalEquality(BigDecimal.ONE, null, CODE, FIELD)
                    .validateBigDecimalEquality(null, null, CODE, FIELD);

            assertThat(alerts).isEmpty();
        }

        @Test
        @DisplayName("la igualdad es numérica: 1.0 y 1.00 son el mismo importe")
        void validateBigDecimalEqualityComparaValorNoEscala() {
            check().validateBigDecimalEquality(new BigDecimal("1.0"), new BigDecimal("1.00"), CODE, FIELD);

            assertThat(alerts).isEmpty();

            check().validateBigDecimalEquality(new BigDecimal("1.0"), new BigDecimal("2.0"), CODE, FIELD);

            assertThat(alerts).hasSize(1);
        }
    }
}
