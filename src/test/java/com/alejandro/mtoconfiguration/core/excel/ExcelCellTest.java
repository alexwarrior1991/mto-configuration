package com.alejandro.mtoconfiguration.core.excel;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La celda es la frontera con Apache POI: si aqui se pierde el tipo original,
 * ningun parser posterior puede recuperarlo. Estos tests fijan ese contrato.
 */
class ExcelCellTest {
    // --- El tipo original se conserva ---

    @Test
    void shouldKeepNumericTypeInsteadOfDegradingToString() {
        ExcelCell cell = ExcelCell.ofNumeric(3, 12.54);

        assertThat(cell.type()).isEqualTo(ExcelCellType.NUMERIC);
        assertThat(cell.asNumber()).contains(12.54);
        assertThat(cell.asBoolean()).isEmpty();
        assertThat(cell.asDate()).isEmpty();
    }

    @Test
    void shouldKeepLeadingZerosWhenValueIsText() {
        // Un codigo "012" es texto en el Excel: convertirlo a numero lo destruiria.
        assertThat(ExcelCell.ofString(0, "012").asString()).contains("012");
    }

    @Test
    void shouldExposeIntegerNumbersWithoutDecimalSuffix() {
        // POI entrega todo numero como Double; "12.0" no sirve como identificador.
        assertThat(ExcelCell.ofNumeric(1, 12d).asString()).contains("12");
    }

    @Test
    void shouldExposeDecimalNumbersAsText() {
        assertThat(ExcelCell.ofNumeric(1, 12.5).asString()).contains("12.5");
    }

    @Test
    void shouldTrimTextBecauseWorkbooksAreFullOfAccidentalSpaces() {
        assertThat(ExcelCell.ofString(0, "  Track 1 ").asString()).contains("Track 1");
    }

    // --- Celdas vacias ---

    @Test
    void shouldTreatBlankCellAsEmpty() {
        ExcelCell cell = ExcelCell.ofBlank(2);

        assertThat(cell.isBlank()).isTrue();
        assertThat(cell.asString()).isEmpty();
        assertThat(cell.asNumber()).isEmpty();
    }

    @Test
    void shouldTreatWhitespaceOnlyTextAsBlank() {
        // Para el importador, " " no aporta informacion: debe comportarse como vacia.
        ExcelCell cell = ExcelCell.ofString(2, "   ");

        assertThat(cell.isBlank()).isTrue();
        assertThat(cell.asString()).isEmpty();
    }

    @Test
    void shouldTreatNullTextAsBlank() {
        assertThat(ExcelCell.ofString(2, null).isBlank()).isTrue();
    }

    // --- Booleanos, fechas, formulas y errores ---

    @Test
    void shouldExposeBooleanValue() {
        assertThat(ExcelCell.ofBoolean(4, true).asBoolean()).contains(true);
    }

    @Test
    void shouldExposeDateValue() {
        LocalDateTime date = LocalDateTime.of(2026, 3, 14, 10, 30);

        assertThat(ExcelCell.ofDate(5, date).asDate()).contains(date);
    }

    @Test
    void shouldExposeEvaluatedResultOfFormulaAndKeepFormulaText() {
        // El parser necesita el valor; la formula solo sirve para diagnosticar.
        ExcelCell cell = ExcelCell.ofFormula(6, 25d, "A1+B1");

        assertThat(cell.type()).isEqualTo(ExcelCellType.FORMULA);
        assertThat(cell.asNumber()).contains(25d);
        assertThat(cell.formulaText()).contains("A1+B1");
    }

    @Test
    void shouldNotReturnValueForErrorCellSoBadDataIsNeverImported() {
        ExcelCell cell = ExcelCell.ofError(7, "#REF!");

        assertThat(cell.isError()).isTrue();
        assertThat(cell.asString()).isEmpty();
    }

    // --- Invariantes ---

    @Test
    void shouldRejectNegativeColumnIndex() {
        assertThatThrownBy(() -> ExcelCell.ofString(-1, "x"))
                .isInstanceOf(ExcelException.class);
    }

    @Test
    void shouldRejectValueThatDoesNotMatchDeclaredType() {
        // Un reader mal escrito no debe poder crear una celda NUMERIC con texto.
        assertThatThrownBy(() -> new ExcelCell(0, ExcelCellType.NUMERIC, "no soy un numero", null))
                .isInstanceOf(ExcelException.class);
    }

    @Test
    void shouldBeComparableByValueSoParsersCanAssertEasily() {
        assertThat(ExcelCell.ofString(0, "A")).isEqualTo(ExcelCell.ofString(0, "A"));
        assertThat(ExcelCell.ofString(0, "A")).isNotEqualTo(ExcelCell.ofString(1, "A"));
    }
}
