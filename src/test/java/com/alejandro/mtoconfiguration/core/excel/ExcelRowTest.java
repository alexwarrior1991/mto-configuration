package com.alejandro.mtoconfiguration.core.excel;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * En los workbooks reales las filas son irregulares: unas acaban antes que otras y
 * otras tienen huecos en medio. Estos tests fijan que esa irregularidad se resuelve
 * aqui y no en cada parser.
 */
class ExcelRowTest {

    // --- Acceso por columna ---

    @Test
    void shouldReturnCellByColumnIndex() {
        ExcelRow row = ExcelRow.of(0,
                ExcelCell.ofString(0, "Track 1"),
                ExcelCell.ofNumeric(1, 12.54));

        assertThat(row.cell(0).asString()).contains("Track 1");
        assertThat(row.cell(1).asNumber()).contains(12.54);
    }

    @Test
    void shouldReturnBlankCellWhenColumnIsBeyondEndOfRow() {
        // La fila fisica acaba en la ultima celda escrita; pedir mas alla es habitual.
        ExcelRow row = ExcelRow.of(3, ExcelCell.ofString(0, "A"));

        ExcelCell cell = row.cell(7);

        assertThat(cell.type()).isEqualTo(ExcelCellType.BLANK);
        assertThat(cell.columnIndex()).isEqualTo(7);
        assertThat(cell.isBlank()).isTrue();
    }

    @Test
    void shouldReturnBlankCellForGapInTheMiddleOfTheRow() {
        // POI no crea celda para una columna nunca escrita: la 1 no existe.
        ExcelRow row = ExcelRow.of(2,
                ExcelCell.ofString(0, "A"),
                ExcelCell.ofString(2, "C"));

        assertThat(row.cell(1).isBlank()).isTrue();
        assertThat(row.cell(2).asString()).contains("C");
    }

    @Test
    void shouldRejectNegativeColumnIndex() {
        assertThatThrownBy(() -> ExcelRow.empty(0).cell(-1))
                .isInstanceOf(ExcelException.class);
    }

    // --- Atajos tipados ---

    @Test
    void shouldDelegateTypedAccessorsToTheCell() {
        LocalDateTime date = LocalDateTime.of(2026, 3, 14, 10, 30);
        ExcelRow row = ExcelRow.of(0,
                ExcelCell.ofString(0, "  Track 1 "),
                ExcelCell.ofNumeric(1, 12.54),
                ExcelCell.ofBoolean(2, true),
                ExcelCell.ofDate(3, date));

        assertThat(row.string(0)).contains("Track 1");
        assertThat(row.number(1)).contains(12.54);
        assertThat(row.bool(2)).contains(true);
        assertThat(row.date(3)).contains(date);
    }

    @Test
    void shouldReturnEmptyOptionalsForMissingBlankAndErrorCells() {
        ExcelRow row = ExcelRow.of(0,
                ExcelCell.ofBlank(0),
                ExcelCell.ofError(1, "#REF!"));

        assertThat(row.string(0)).isEmpty();
        assertThat(row.string(1)).isEmpty();
        assertThat(row.number(9)).isEmpty();
        assertThat(row.bool(9)).isEmpty();
        assertThat(row.date(9)).isEmpty();
    }

    // --- Filas vacias ---

    @Test
    void shouldTreatRowWithoutCellsAsEmpty() {
        assertThat(ExcelRow.empty(5).isEmpty()).isTrue();
        assertThat(ExcelRow.empty(5).size()).isZero();
    }

    @Test
    void shouldTreatRowOfBlankAndWhitespaceCellsAsEmpty() {
        // Fila separadora tipica: el parser debe poder saltarla sin analizarla.
        ExcelRow row = ExcelRow.of(6,
                ExcelCell.ofBlank(0),
                ExcelCell.ofString(1, "   "));

        assertThat(row.isEmpty()).isTrue();
    }

    @Test
    void shouldNotTreatRowAsEmptyWhenASingleCellHasContent() {
        ExcelRow row = ExcelRow.of(6,
                ExcelCell.ofBlank(0),
                ExcelCell.ofString(1, "Via 2"));

        assertThat(row.isEmpty()).isFalse();
    }

    // --- Inmutabilidad e invariantes ---

    @Test
    void shouldCopyCellsSoLaterChangesToTheSourceListDoNotAffectTheRow() {
        List<ExcelCell> source = new ArrayList<>(List.of(ExcelCell.ofString(0, "A")));
        ExcelRow row = new ExcelRow(0, source);

        source.add(ExcelCell.ofString(1, "B"));

        assertThat(row.size()).isEqualTo(1);
    }

    @Test
    void shouldExposeAnUnmodifiableCellList() {
        ExcelRow row = ExcelRow.of(0, ExcelCell.ofString(0, "A"));

        assertThatThrownBy(() -> row.cells().add(ExcelCell.ofString(1, "B")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectNegativeRowIndex() {
        assertThatThrownBy(() -> ExcelRow.empty(-1))
                .isInstanceOf(ExcelException.class);
    }

    @Test
    void shouldRejectNullCellList() {
        assertThatThrownBy(() -> new ExcelRow(0, null))
                .isInstanceOf(ExcelException.class);
    }

    @Test
    void shouldRejectNullCellsBecauseTheyWouldReachTheParsersAsNulls() {
        List<ExcelCell> withNull = Arrays.asList(ExcelCell.ofString(0, "A"), null);

        assertThatThrownBy(() -> new ExcelRow(0, withNull))
                .isInstanceOf(ExcelException.class);
    }

    @Test
    void shouldBeComparableByValueSoParsersCanAssertEasily() {
        assertThat(ExcelRow.of(0, ExcelCell.ofString(0, "A")))
                .isEqualTo(ExcelRow.of(0, ExcelCell.ofString(0, "A")));
        assertThat(ExcelRow.of(0, ExcelCell.ofString(0, "A")))
                .isNotEqualTo(ExcelRow.of(1, ExcelCell.ofString(0, "A")));
    }
}
