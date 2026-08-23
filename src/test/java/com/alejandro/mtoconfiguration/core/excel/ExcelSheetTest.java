package com.alejandro.mtoconfiguration.core.excel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La hoja es la unidad que el parser de Track recibira entera. Estos tests fijan
 * que ninguna irregularidad de la hoja (filas ausentes, separadoras, hojas de
 * plantilla vacias) obligue al parser a programar defensivamente.
 */
class ExcelSheetTest {

    // --- Acceso por fila ---

    @Test
    void shouldReturnRowByIndex() {
        ExcelSheet sheet = ExcelSheet.of("Via 1", 0,
                ExcelRow.of(0, ExcelCell.ofString(0, "Cabecera")),
                ExcelRow.of(1, ExcelCell.ofNumeric(0, 12.54)));

        assertThat(sheet.row(0).string(0)).contains("Cabecera");
        assertThat(sheet.row(1).number(0)).contains(12.54);
    }

    @Test
    void shouldReturnEmptyRowWhenIndexIsBeyondEndOfSheet() {
        ExcelSheet sheet = ExcelSheet.of("Via 1", 0, ExcelRow.of(0, ExcelCell.ofString(0, "A")));

        ExcelRow row = sheet.row(50);

        assertThat(row.rowIndex()).isEqualTo(50);
        assertThat(row.isEmpty()).isTrue();
    }

    @Test
    void shouldReturnEmptyRowForGapInTheMiddleOfTheSheet() {
        // POI no crea fila alguna para una fila nunca escrita: la 1 no existe.
        ExcelSheet sheet = ExcelSheet.of("Via 1", 0,
                ExcelRow.of(0, ExcelCell.ofString(0, "A")),
                ExcelRow.of(2, ExcelCell.ofString(0, "C")));

        assertThat(sheet.row(1).isEmpty()).isTrue();
        assertThat(sheet.row(2).string(0)).contains("C");
    }

    @Test
    void shouldRejectNegativeRowIndex() {
        assertThatThrownBy(() -> ExcelSheet.empty("Via 1", 0).row(-1))
                .isInstanceOf(ExcelException.class);
    }

    // --- Acceso por coordenadas ---

    @Test
    void shouldReadCellByCoordinates() {
        ExcelSheet sheet = ExcelSheet.of("Via 1", 0,
                ExcelRow.of(0, ExcelCell.ofString(0, "A"), ExcelCell.ofString(1, "B")));

        assertThat(sheet.cell(0, 1).asString()).contains("B");
    }

    @Test
    void shouldReturnBlankCellWhenRowOrColumnDoesNotExist() {
        ExcelSheet sheet = ExcelSheet.of("Via 1", 0,
                ExcelRow.of(0, ExcelCell.ofString(0, "A")));

        assertThat(sheet.cell(9, 9).type()).isEqualTo(ExcelCellType.BLANK);
        assertThat(sheet.cell(0, 9).isBlank()).isTrue();
    }

    // --- Filas con contenido ---

    @Test
    void shouldFilterOutSeparatorAndWhitespaceRows() {
        ExcelSheet sheet = ExcelSheet.of("Via 1", 0,
                ExcelRow.of(0, ExcelCell.ofString(0, "Cabecera")),
                ExcelRow.empty(1),
                ExcelRow.of(2, ExcelCell.ofString(0, "   ")),
                ExcelRow.of(3, ExcelCell.ofString(0, "Dato")));

        assertThat(sheet.nonEmptyRows())
                .extracting(ExcelRow::rowIndex)
                .containsExactly(0, 3);
    }

    @Test
    void shouldExposeAnUnmodifiableListOfNonEmptyRows() {
        ExcelSheet sheet = ExcelSheet.of("Via 1", 0, ExcelRow.of(0, ExcelCell.ofString(0, "A")));

        assertThatThrownBy(() -> sheet.nonEmptyRows().add(ExcelRow.empty(1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- Hojas vacias ---

    @Test
    void shouldTreatSheetWithoutRowsAsEmpty() {
        assertThat(ExcelSheet.empty("Plantilla", 2).isEmpty()).isTrue();
        assertThat(ExcelSheet.empty("Plantilla", 2).rowCount()).isZero();
    }

    @Test
    void shouldTreatSheetOfOnlyEmptyRowsAsEmpty() {
        // Hoja de plantilla: hay que ignorarla sin que la importacion falle.
        ExcelSheet sheet = ExcelSheet.of("Plantilla", 1,
                ExcelRow.empty(0),
                ExcelRow.of(1, ExcelCell.ofString(0, "  ")));

        assertThat(sheet.isEmpty()).isTrue();
    }

    @Test
    void shouldNotTreatSheetAsEmptyWhenASingleRowHasContent() {
        ExcelSheet sheet = ExcelSheet.of("Via 1", 0,
                ExcelRow.empty(0),
                ExcelRow.of(1, ExcelCell.ofString(0, "Via 2")));

        assertThat(sheet.isEmpty()).isFalse();
    }

    // --- Busqueda de la cabecera ---

    @Test
    void shouldFindFirstRowMatchingTheCondition() {
        ExcelSheet sheet = ExcelSheet.of("Via 1", 0,
                ExcelRow.empty(0),
                ExcelRow.of(1, ExcelCell.ofString(0, "KP")),
                ExcelRow.of(2, ExcelCell.ofString(0, "KP")));

        assertThat(sheet.findFirstRow(row -> row.string(0).filter("KP"::equals).isPresent()))
                .map(ExcelRow::rowIndex)
                .contains(1);
    }

    @Test
    void shouldReturnEmptyWhenNoRowMatchesTheCondition() {
        ExcelSheet sheet = ExcelSheet.of("Via 1", 0, ExcelRow.of(0, ExcelCell.ofString(0, "A")));

        assertThat(sheet.findFirstRow(row -> row.string(0).filter("KP"::equals).isPresent())).isEmpty();
    }

    // --- Inmutabilidad e invariantes ---

    @Test
    void shouldCopyRowsSoLaterChangesToTheSourceListDoNotAffectTheSheet() {
        List<ExcelRow> source = new ArrayList<>(List.of(ExcelRow.of(0, ExcelCell.ofString(0, "A"))));
        ExcelSheet sheet = new ExcelSheet("Via 1", 0, source);

        source.add(ExcelRow.empty(1));

        assertThat(sheet.rowCount()).isEqualTo(1);
    }

    @Test
    void shouldExposeAnUnmodifiableRowList() {
        ExcelSheet sheet = ExcelSheet.of("Via 1", 0, ExcelRow.of(0, ExcelCell.ofString(0, "A")));

        assertThatThrownBy(() -> sheet.rows().add(ExcelRow.empty(1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectBlankSheetName() {
        assertThatThrownBy(() -> ExcelSheet.empty("   ", 0))
                .isInstanceOf(ExcelException.class);
        assertThatThrownBy(() -> ExcelSheet.empty(null, 0))
                .isInstanceOf(ExcelException.class);
    }

    @Test
    void shouldRejectNegativeSheetIndex() {
        assertThatThrownBy(() -> ExcelSheet.empty("Via 1", -1))
                .isInstanceOf(ExcelException.class);
    }

    @Test
    void shouldRejectNullRowListAndNullRows() {
        assertThatThrownBy(() -> new ExcelSheet("Via 1", 0, null))
                .isInstanceOf(ExcelException.class);

        List<ExcelRow> withNull = Arrays.asList(ExcelRow.empty(0), null);
        assertThatThrownBy(() -> new ExcelSheet("Via 1", 0, withNull))
                .isInstanceOf(ExcelException.class);
    }

    @Test
    void shouldBeComparableByValueSoParsersCanAssertEasily() {
        assertThat(ExcelSheet.of("Via 1", 0, ExcelRow.empty(0)))
                .isEqualTo(ExcelSheet.of("Via 1", 0, ExcelRow.empty(0)));
        assertThat(ExcelSheet.of("Via 1", 0, ExcelRow.empty(0)))
                .isNotEqualTo(ExcelSheet.of("Via 2", 0, ExcelRow.empty(0)));
    }
}
