package com.alejandro.mtoconfiguration.core.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El reader es la frontera con Apache POI: todo lo que no resuelva aqui acabaria
 * repetido en cada parser. Los ficheros se generan en memoria para no meter
 * binarios en el repositorio y para que cada test describa exactamente el caso.
 */
class ExcelReaderTest {

    private final ExcelReader reader = new ExcelReader();

    // --- Tipos de celda ---

    @Test
    void shouldReadEachCellWithItsOriginalType() {
        ExcelWorkbook workbook = read(wb -> {
            Row row = wb.createSheet("Via 1").createRow(0);
            row.createCell(0).setCellValue("Texto");
            row.createCell(1).setCellValue(12.5);
            row.createCell(2).setCellValue(true);
        });

        ExcelSheet sheet = workbook.sheet("Via 1").orElseThrow();
        assertThat(sheet.cell(0, 0).type()).isEqualTo(ExcelCellType.STRING);
        assertThat(sheet.cell(0, 0).asString()).contains("Texto");
        assertThat(sheet.cell(0, 1).type()).isEqualTo(ExcelCellType.NUMERIC);
        assertThat(sheet.cell(0, 1).asNumber()).contains(12.5);
        assertThat(sheet.cell(0, 2).type()).isEqualTo(ExcelCellType.BOOLEAN);
        assertThat(sheet.cell(0, 2).asBoolean()).contains(true);
    }

    @Test
    void shouldReadFormattedDateAsDateInsteadOfNumber() {
        // POI guarda las fechas como numeros: sin mirar el formato, un 45000.0
        // llegaria al parser como si fuera un KP.
        LocalDateTime date = LocalDateTime.of(2026, 3, 14, 0, 0);

        ExcelWorkbook workbook = read(wb -> {
            CellStyle style = wb.createCellStyle();
            style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));

            Cell cell = wb.createSheet("Via 1").createRow(0).createCell(0);
            cell.setCellValue(date);
            cell.setCellStyle(style);
        });

        ExcelCell cell = workbook.sheet("Via 1").orElseThrow().cell(0, 0);
        assertThat(cell.type()).isEqualTo(ExcelCellType.DATE);
        assertThat(cell.asDate()).contains(date);
    }

    @Test
    void shouldReadFormulaWithItsEvaluatedResultAndKeepTheFormulaText() {
        ExcelWorkbook workbook = read(wb -> {
            Row row = wb.createSheet("Via 1").createRow(0);
            row.createCell(0).setCellValue(10d);
            row.createCell(1).setCellValue(15d);
            row.createCell(2).setCellFormula("A1+B1");
        });

        ExcelCell cell = workbook.sheet("Via 1").orElseThrow().cell(0, 2);
        assertThat(cell.type()).isEqualTo(ExcelCellType.FORMULA);
        assertThat(cell.asNumber()).contains(25d);
        assertThat(cell.formulaText()).contains("A1+B1");
    }

    @Test
    void shouldReadErrorCellWithoutAbortingTheWholeReading() {
        // Un #DIV/0! es un dato malo, no un fichero ilegible: el importador decidira.
        ExcelWorkbook workbook = read(wb -> {
            Row row = wb.createSheet("Via 1").createRow(0);
            row.createCell(0).setCellErrorValue(FormulaError.DIV0.getCode());
            row.createCell(1).setCellValue("Sigue leyendo");
        });

        ExcelSheet sheet = workbook.sheet("Via 1").orElseThrow();
        assertThat(sheet.cell(0, 0).isError()).isTrue();
        assertThat(sheet.cell(0, 0).asString()).isEmpty();
        assertThat(sheet.cell(0, 1).asString()).contains("Sigue leyendo");
    }

    @Test
    void shouldReadFormulaThatEvaluatesToErrorAsAnErrorCell() {
        ExcelWorkbook workbook = read(wb -> {
            Row row = wb.createSheet("Via 1").createRow(0);
            row.createCell(0).setCellValue(1d);
            row.createCell(1).setCellValue(0d);
            row.createCell(2).setCellFormula("A1/B1");
        });

        assertThat(workbook.sheet("Via 1").orElseThrow().cell(0, 2).isError()).isTrue();
    }

    // --- Huecos ---

    @Test
    void shouldMaterializeNeverWrittenCellsAsBlankInsteadOfNull() {
        ExcelWorkbook workbook = read(wb -> {
            Row row = wb.createSheet("Via 1").createRow(0);
            row.createCell(0).setCellValue("A");
            row.createCell(3).setCellValue("D");
        });

        ExcelSheet sheet = workbook.sheet("Via 1").orElseThrow();
        assertThat(sheet.cell(0, 1).isBlank()).isTrue();
        assertThat(sheet.cell(0, 2).type()).isEqualTo(ExcelCellType.BLANK);
        assertThat(sheet.cell(0, 3).asString()).contains("D");
        // Una columna mas alla del final tampoco puede romper.
        assertThat(sheet.cell(0, 40).isBlank()).isTrue();
    }

    @Test
    void shouldMaterializeNeverWrittenRowsAsEmptyRows() {
        ExcelWorkbook workbook = read(wb -> {
            Sheet sheet = wb.createSheet("Via 1");
            sheet.createRow(0).createCell(0).setCellValue("Cabecera");
            sheet.createRow(2).createCell(0).setCellValue("Dato");
        });

        ExcelSheet sheet = workbook.sheet("Via 1").orElseThrow();
        assertThat(sheet.rowCount()).isEqualTo(3);
        assertThat(sheet.row(1).isEmpty()).isTrue();
        assertThat(sheet.nonEmptyRows()).hasSize(2);
    }

    // --- Celdas combinadas ---

    @Test
    void shouldPropagateMergedCellValueToTheWholeRange() {
        // POI guarda el valor solo en la esquina superior izquierda; sin propagarlo
        // el parser leeria vacio en columnas que visualmente tienen contenido.
        ExcelWorkbook workbook = read(wb -> {
            Sheet sheet = wb.createSheet("Via 1");
            sheet.createRow(0).createCell(0).setCellValue("Cabecera combinada");
            sheet.createRow(1);
            sheet.addMergedRegion(new CellRangeAddress(0, 1, 0, 2));
        });

        ExcelSheet sheet = workbook.sheet("Via 1").orElseThrow();
        assertThat(sheet.cell(0, 0).asString()).contains("Cabecera combinada");
        assertThat(sheet.cell(0, 2).asString()).contains("Cabecera combinada");
        assertThat(sheet.cell(1, 1).asString()).contains("Cabecera combinada");
        assertThat(sheet.cell(1, 2).asString()).contains("Cabecera combinada");
    }

    // --- Estructura del workbook ---

    @Test
    void shouldReadEverySheetWithItsNameAndIndex() {
        ExcelWorkbook workbook = read(wb -> {
            wb.createSheet("Via 1").createRow(0).createCell(0).setCellValue("A");
            wb.createSheet("Via 2").createRow(0).createCell(0).setCellValue("B");
        });

        assertThat(workbook.sheetNames()).containsExactly("Via 1", "Via 2");
        assertThat(workbook.sheet(1)).map(ExcelSheet::name).contains("Via 2");
    }

    @Test
    void shouldReadTemplateSheetWithoutContentAsAnEmptySheet() {
        ExcelWorkbook workbook = read(wb -> {
            wb.createSheet("Plantilla");
            wb.createSheet("Via 1").createRow(0).createCell(0).setCellValue("A");
        });

        assertThat(workbook.sheet("Plantilla").orElseThrow().isEmpty()).isTrue();
        assertThat(workbook.nonEmptySheets()).extracting(ExcelSheet::name).containsExactly("Via 1");
    }

    // --- Errores y recursos ---

    @Test
    void shouldFailWithExcelExceptionWhenTheStreamIsNotAnExcelFile() {
        InputStream notAnExcel = new ByteArrayInputStream("no soy un excel".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> reader.read(notAnExcel)).isInstanceOf(ExcelException.class);
    }

    @Test
    void shouldFailWithExcelExceptionWhenThereIsNoFile() {
        assertThatThrownBy(() -> reader.read(null)).isInstanceOf(ExcelException.class);
    }

    @Test
    void shouldCloseTheStreamAlsoWhenTheFileIsInvalid() {
        // El fichero llega de una subida HTTP: dejar el stream abierto filtra recursos.
        TrackedInputStream valid = new TrackedInputStream(bytesOf(
                wb -> wb.createSheet("Via 1").createRow(0).createCell(0).setCellValue("A")));
        reader.read(valid);
        assertThat(valid.closed).isTrue();

        TrackedInputStream invalid = new TrackedInputStream("no soy un excel".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> reader.read(invalid)).isInstanceOf(ExcelException.class);
        assertThat(invalid.closed).isTrue();
    }

    // --- helpers ---

    private ExcelWorkbook read(Consumer<Workbook> content) {
        return reader.read(new ByteArrayInputStream(bytesOf(content)));
    }

    private byte[] bytesOf(Consumer<Workbook> content) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            content.accept(workbook);
            workbook.write(output);
            return output.toByteArray();

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class TrackedInputStream extends ByteArrayInputStream {

        private boolean closed;

        private TrackedInputStream(byte[] content) {
            super(content);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
