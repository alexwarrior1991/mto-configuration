package com.alejandro.mtoconfiguration.core.excel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El workbook es la raiz del modelo generico y la puerta de entrada del futuro
 * importador. Estos tests fijan que localizar una hoja nunca dependa de como se
 * haya escrito su nombre y que un workbook mal construido se detecte al crearlo.
 */
class ExcelWorkbookTest {

    // --- Acceso por nombre ---

    @Test
    void shouldReturnSheetByName() {
        ExcelWorkbook workbook = ExcelWorkbook.of(
                ExcelSheet.of("Via 1", 0, ExcelRow.of(0, ExcelCell.ofString(0, "A"))),
                ExcelSheet.empty("Via 2", 1));

        assertThat(workbook.sheet("Via 1"))
                .map(ExcelSheet::index)
                .contains(0);
    }

    @Test
    void shouldIgnoreCaseAndSurroundingSpacesWhenLookingUpBySheetName() {
        // El mismo nombre aparece escrito de formas distintas en los ficheros reales.
        ExcelWorkbook workbook = ExcelWorkbook.of(ExcelSheet.empty("Via 1", 0));

        assertThat(workbook.sheet("  via 1 ")).isPresent();
        assertThat(workbook.sheet("VIA 1")).isPresent();
    }

    @Test
    void shouldReturnEmptyWhenSheetNameDoesNotExist() {
        ExcelWorkbook workbook = ExcelWorkbook.of(ExcelSheet.empty("Via 1", 0));

        assertThat(workbook.sheet("Via 9")).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenSheetNameIsNullOrBlank() {
        ExcelWorkbook workbook = ExcelWorkbook.of(ExcelSheet.empty("Via 1", 0));

        assertThat(workbook.sheet((String) null)).isEmpty();
        assertThat(workbook.sheet("   ")).isEmpty();
    }

    // --- Acceso por indice ---

    @Test
    void shouldReturnSheetByIndex() {
        ExcelWorkbook workbook = ExcelWorkbook.of(
                ExcelSheet.empty("Via 1", 0),
                ExcelSheet.empty("Via 2", 1));

        assertThat(workbook.sheet(1))
                .map(ExcelSheet::name)
                .contains("Via 2");
    }

    @Test
    void shouldFindSheetByItsRealIndexEvenWhenItIsNotItsPositionInTheList() {
        // Un reader que salte hojas ocultas deja indices que no coinciden con la posicion.
        ExcelWorkbook workbook = ExcelWorkbook.of(
                ExcelSheet.empty("Via 1", 0),
                ExcelSheet.empty("Via 3", 2));

        assertThat(workbook.sheet(2)).map(ExcelSheet::name).contains("Via 3");
        assertThat(workbook.sheet(1)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenIndexIsBeyondTheLastSheet() {
        assertThat(ExcelWorkbook.of(ExcelSheet.empty("Via 1", 0)).sheet(7)).isEmpty();
    }

    @Test
    void shouldRejectNegativeSheetIndex() {
        assertThatThrownBy(() -> ExcelWorkbook.empty().sheet(-1))
                .isInstanceOf(ExcelException.class);
    }

    // --- Vista general del workbook ---

    @Test
    void shouldExposeSheetNamesInOrderAndAsTheyAreWrittenInTheFile() {
        ExcelWorkbook workbook = ExcelWorkbook.of(
                ExcelSheet.empty("Via 1", 0),
                ExcelSheet.empty("Via 2", 1));

        assertThat(workbook.sheetNames()).containsExactly("Via 1", "Via 2");
        assertThat(workbook.sheetCount()).isEqualTo(2);
    }

    @Test
    void shouldFilterOutTemplateSheetsWithoutContent() {
        ExcelWorkbook workbook = ExcelWorkbook.of(
                ExcelSheet.of("Via 1", 0, ExcelRow.of(0, ExcelCell.ofString(0, "Dato"))),
                ExcelSheet.empty("Plantilla", 1),
                ExcelSheet.of("Notas", 2, ExcelRow.of(0, ExcelCell.ofString(0, "   "))));

        assertThat(workbook.nonEmptySheets())
                .extracting(ExcelSheet::name)
                .containsExactly("Via 1");
    }

    @Test
    void shouldTreatWorkbookWithoutContentAsEmpty() {
        assertThat(ExcelWorkbook.empty().isEmpty()).isTrue();
        assertThat(ExcelWorkbook.of(ExcelSheet.empty("Plantilla", 0)).isEmpty()).isTrue();
    }

    @Test
    void shouldNotTreatWorkbookAsEmptyWhenASingleSheetHasContent() {
        ExcelWorkbook workbook = ExcelWorkbook.of(
                ExcelSheet.empty("Plantilla", 0),
                ExcelSheet.of("Via 1", 1, ExcelRow.of(0, ExcelCell.ofString(0, "Dato"))));

        assertThat(workbook.isEmpty()).isFalse();
    }

    // --- Inmutabilidad e invariantes ---

    @Test
    void shouldCopySheetsSoLaterChangesToTheSourceListDoNotAffectTheWorkbook() {
        List<ExcelSheet> source = new ArrayList<>(List.of(ExcelSheet.empty("Via 1", 0)));
        ExcelWorkbook workbook = new ExcelWorkbook(source);

        source.add(ExcelSheet.empty("Via 2", 1));

        assertThat(workbook.sheetCount()).isEqualTo(1);
    }

    @Test
    void shouldExposeUnmodifiableLists() {
        ExcelWorkbook workbook = ExcelWorkbook.of(
                ExcelSheet.of("Via 1", 0, ExcelRow.of(0, ExcelCell.ofString(0, "A"))));

        assertThatThrownBy(() -> workbook.sheets().add(ExcelSheet.empty("Via 2", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> workbook.nonEmptySheets().add(ExcelSheet.empty("Via 2", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> workbook.sheetNames().add("Via 2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectNullSheetListAndNullSheets() {
        assertThatThrownBy(() -> new ExcelWorkbook(null))
                .isInstanceOf(ExcelException.class);

        List<ExcelSheet> withNull = Arrays.asList(ExcelSheet.empty("Via 1", 0), null);
        assertThatThrownBy(() -> new ExcelWorkbook(withNull))
                .isInstanceOf(ExcelException.class);
    }

    @Test
    void shouldRejectDuplicatedSheetNamesBecauseLookupWouldBeAmbiguous() {
        // Excel no admite dos hojas con el mismo nombre: si llegan, el reader falla.
        assertThatThrownBy(() -> ExcelWorkbook.of(
                ExcelSheet.empty("Via 1", 0),
                ExcelSheet.empty("VIA 1", 1)))
                .isInstanceOf(ExcelException.class);
    }

    @Test
    void shouldBeComparableByValueSoParsersCanAssertEasily() {
        assertThat(ExcelWorkbook.of(ExcelSheet.empty("Via 1", 0)))
                .isEqualTo(ExcelWorkbook.of(ExcelSheet.empty("Via 1", 0)));
        assertThat(ExcelWorkbook.of(ExcelSheet.empty("Via 1", 0)))
                .isNotEqualTo(ExcelWorkbook.of(ExcelSheet.empty("Via 2", 0)));
    }
}
