package com.alejandro.mtoconfiguration.service.lov.imports;

import com.alejandro.mtoconfiguration.core.excel.ExcelException;
import com.alejandro.mtoconfiguration.model.synchronous.lov.imports.LovMasterRow;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comportamiento del lector del catalogo maestro.
 *
 * <p>El maestro se edita a MANO antes de importarlo, asi que el error mas probable de
 * todo el flujo no es un fallo de codigo sino una hoja mal editada: una columna
 * renombrada, un ENABLED escrito de otra forma, una fila a medias. Estos tests fijan
 * que el parser falle con un mensaje util en el primer caso y que no se invente datos
 * en los otros.
 */
@DisplayName("LovMasterParser")
class LovMasterParserTest {

    private static final String[] HEADERS = {
            "ENTIDAD", "CODIGO", "DESCRIPCION_EN", "DESCRIPCION_ES", "TIPO",
            "N_PLANO", "ENABLED", "ORIGEN", "CATEGORIAS_BOQ", "EPS",
            "USOS_EN_TRACKS", "REVISAR"
    };

    private final LovMasterParser parser = new LovMasterParser();

    @Nested
    @DisplayName("cuando la hoja esta bien formada")
    class HojaCorrecta {

        @Test
        @DisplayName("lee codigo, descripciones, tipo y numero de plano")
        void leeLasColumnasDeCadaFila() throws IOException {
            InputStream excel = workbook(
                    row("Foundation", "C3R", "Foundation for POLE HEB-240", "Cimentacion para poste",
                            "CX", "6901", "SI"));

            List<LovMasterRow> rows = parser.parseLovs(excel);

            assertThat(rows).hasSize(1);
            LovMasterRow row = rows.getFirst();
            assertThat(row.entity()).isEqualTo("Foundation");
            assertThat(row.code()).isEqualTo("C3R");
            assertThat(row.descriptionEn()).isEqualTo("Foundation for POLE HEB-240");
            assertThat(row.type()).isEqualTo("CX");
            assertThat(row.drawingNumber()).isEqualTo(6901L);
            assertThat(row.enabled()).isTrue();
        }

        @Test
        @DisplayName("numera las filas como las ve Excel, para poder senalarlas en el informe")
        void conservaElNumeroDeFilaDeExcel() throws IOException {
            InputStream excel = workbook(
                    row("Sectioning", "S/A", "Overlap Semi-Axis", "", "", "", "SI"),
                    row("Sectioning", "A/S", "Overlap Anchorage", "", "", "", "SI"));

            List<LovMasterRow> rows = parser.parseLovs(excel);

            // Fila 1 es la cabecera: los datos empiezan en la 2.
            assertThat(rows).extracting(LovMasterRow::sourceRow).containsExactly(2, 3);
        }

        @Test
        @DisplayName("prefiere la descripcion inglesa y cae a la espanola si no hay")
        void eligeLaDescripcionAPersistir() throws IOException {
            InputStream excel = workbook(
                    row("PoleType", "HEB-240", "OCS Pole", "Poste OCS", "", "", "SI"),
                    row("PoleType", "HEB-260", "", "Poste OCS grande", "", "", "SI"),
                    row("PoleType", "HEB-280", "", "", "", "", "SI"));

            List<LovMasterRow> rows = parser.parseLovs(excel);

            assertThat(rows).extracting(LovMasterRow::description)
                    .containsExactly("OCS Pole", "Poste OCS grande", "HEB-280");
        }
    }

    @Nested
    @DisplayName("cuando la hoja viene editada a mano")
    class HojaEditada {

        @Test
        @DisplayName("acepta las formas habituales de escribir ENABLED")
        void interpretaEnabledConMangaAncha() throws IOException {
            InputStream excel = workbook(
                    row("Sectioning", "A", "", "", "", "", "SI"),
                    row("Sectioning", "B", "", "", "", "", "sí"),
                    row("Sectioning", "C", "", "", "", "", "x"),
                    row("Sectioning", "D", "", "", "", "", "TRUE"),
                    row("Sectioning", "E", "", "", "", "", "NO"),
                    row("Sectioning", "F", "", "", "", "", ""));

            List<LovMasterRow> rows = parser.parseLovs(excel);

            assertThat(rows).extracting(LovMasterRow::enabled)
                    .containsExactly(true, true, true, true, false, false);
        }

        @Test
        @DisplayName("ignora las filas sin entidad o sin codigo en vez de inventarselas")
        void saltaFilasIncompletas() throws IOException {
            InputStream excel = workbook(
                    row("Foundation", "C3R", "", "", "CX", "", "SI"),
                    row("", "C4R", "", "", "CX", "", "SI"),
                    row("Foundation", "", "", "", "CX", "", "SI"));

            List<LovMasterRow> rows = parser.parseLovs(excel);

            assertThat(rows).extracting(LovMasterRow::code).containsExactly("C3R");
        }

        @Test
        @DisplayName("un numero de plano no numerico no rompe la lectura: queda a nulo")
        void toleraNumeroDePlanoNoNumerico() throws IOException {
            InputStream excel = workbook(
                    row("Foundation", "C3R", "", "", "CX", "DW7035-A", "SI"));

            assertThat(parser.parseLovs(excel).getFirst().drawingNumber()).isNull();
        }
    }

    @Nested
    @DisplayName("cuando la hoja esta mal")
    class HojaInvalida {

        @Test
        @DisplayName("dice que columna falta, en lugar de importar a medias")
        void fallaSiFaltaUnaColumnaObligatoria() throws IOException {
            InputStream excel = workbookWithHeaders(
                    new String[]{"ENTIDAD", "DESCRIPCION_EN"},
                    new String[]{"Foundation", "algo"});

            assertThatThrownBy(() -> parser.parseLovs(excel))
                    .isInstanceOf(ExcelException.class)
                    .hasMessageContaining("CODIGO");
        }

        @Test
        @DisplayName("dice que hojas hay cuando no encuentra la que busca")
        void fallaSiNoExisteLaHoja() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                workbook.createSheet("OTRA COSA");
                workbook.write(out);
            }

            assertThatThrownBy(() -> parser.parseLovs(new ByteArrayInputStream(out.toByteArray())))
                    .isInstanceOf(ExcelException.class)
                    .hasMessageContaining("LOVS")
                    .hasMessageContaining("OTRA COSA");
        }
    }

    // --- utilidades ---------------------------------------------------------

    private static String[] row(String entity, String code, String descEn, String descEs,
                                String type, String drawing, String enabled) {
        return new String[]{entity, code, descEn, descEs, type, drawing, enabled,
                "BOQ", "", "EP4", "0", "NO"};
    }

    private InputStream workbook(String[]... rows) throws IOException {
        return workbookWithHeaders(HEADERS, rows);
    }

    private InputStream workbookWithHeaders(String[] headers, String[]... rows) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(LovMasterParser.LOVS_SHEET);
            writeRow(sheet.createRow(0), headers);
            for (int index = 0; index < rows.length; index++) {
                writeRow(sheet.createRow(index + 1), rows[index]);
            }
            // parseAll tambien pide la hoja de tipos.
            Sheet types = workbook.createSheet(LovMasterParser.TYPES_SHEET);
            writeRow(types.createRow(0),
                    new String[]{"ENTIDAD_TIPO", "CODIGO", "DESCRIPCION_EN", "ENABLED"});
            workbook.write(out);
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    private void writeRow(Row row, String[] values) {
        for (int index = 0; index < values.length; index++) {
            row.createCell(index).setCellValue(values[index]);
        }
    }
}
