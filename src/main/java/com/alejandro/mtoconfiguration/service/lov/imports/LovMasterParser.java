package com.alejandro.mtoconfiguration.service.lov.imports;

import com.alejandro.mtoconfiguration.core.excel.ExcelCell;
import com.alejandro.mtoconfiguration.core.excel.ExcelException;
import com.alejandro.mtoconfiguration.core.excel.ExcelReader;
import com.alejandro.mtoconfiguration.core.excel.ExcelRow;
import com.alejandro.mtoconfiguration.core.excel.ExcelSheet;
import com.alejandro.mtoconfiguration.core.excel.ExcelWorkbook;
import com.alejandro.mtoconfiguration.model.synchronous.lov.imports.LovMasterRow;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lee el catalogo maestro {@code lov-master.xlsx}.
 *
 * <p>A diferencia de los workbooks de Execution Package, el maestro tiene un formato
 * fijo y documentado, asi que este parser puede permitirse ser estricto: si falta una
 * columna obligatoria falla con un mensaje que dice cual, en lugar de continuar y
 * cargar datos a medias. El maestro se edita a mano antes de importarlo, y equivocarse
 * al editarlo es el error mas probable de todo el flujo.
 *
 * <p>Las columnas se localizan por <b>nombre</b>, no por posicion, para que anadir una
 * columna informativa al maestro no rompa la importacion.
 */
@Component
public class LovMasterParser {

    /** Hoja con los valores de LOV. */
    public static final String LOVS_SHEET = "LOVS";

    /** Hoja con los catalogos *Type, que se cargan antes que las LOV que los referencian. */
    public static final String TYPES_SHEET = "TIPOS";

    private static final String COL_ENTITY = "ENTIDAD";
    private static final String COL_CODE = "CODIGO";
    private static final String COL_DESC_EN = "DESCRIPCION_EN";
    private static final String COL_DESC_ES = "DESCRIPCION_ES";
    private static final String COL_TYPE = "TIPO";
    private static final String COL_DRAWING = "N_PLANO";
    private static final String COL_ENABLED = "ENABLED";

    private static final String COL_TYPE_ENTITY = "ENTIDAD_TIPO";

    private static final int HEADER_ROW = 0;
    private static final int FIRST_DATA_ROW = 1;

    private final ExcelReader excelReader = new ExcelReader();

    /** Valores de la hoja LOVS. El InputStream se cierra siempre. */
    public List<LovMasterRow> parseLovs(InputStream inputStream) {
        ExcelWorkbook workbook = excelReader.read(inputStream);
        return readSheet(workbook, LOVS_SHEET, COL_ENTITY);
    }

    /**
     * Lee las dos hojas de una sola pasada.
     *
     * <p>Importa hacerlo asi porque {@link ExcelReader} consume el InputStream: leer el
     * fichero dos veces obligaria a rebobinarlo o a guardarlo en memoria aparte.
     */
    public LovMasterContent parseAll(InputStream inputStream) {
        ExcelWorkbook workbook = excelReader.read(inputStream);
        return new LovMasterContent(
                readSheet(workbook, LOVS_SHEET, COL_ENTITY),
                readSheet(workbook, TYPES_SHEET, COL_TYPE_ENTITY)
        );
    }

    private List<LovMasterRow> readSheet(ExcelWorkbook workbook, String sheetName, String entityColumn) {
        ExcelSheet sheet = workbook.sheet(sheetName)
                .orElseThrow(() -> new ExcelException(
                        "El fichero no tiene la hoja '" + sheetName + "'. Hojas encontradas: "
                                + workbook.sheetNames()));

        if (sheet.rowCount() <= FIRST_DATA_ROW) {
            return List.of();
        }

        Map<String, Integer> columns = readHeader(sheet, sheetName, entityColumn);

        List<LovMasterRow> rows = new ArrayList<>();
        for (int index = FIRST_DATA_ROW; index < sheet.rowCount(); index++) {
            ExcelRow row = sheet.row(index);
            if (row.isEmpty()) {
                continue;
            }

            String entity = text(row, columns.get(entityColumn));
            String code = text(row, columns.get(COL_CODE));
            if (entity.isBlank() || code.isBlank()) {
                continue;
            }

            rows.add(new LovMasterRow(
                    entity,
                    code,
                    text(row, columns.get(COL_DESC_EN)),
                    text(row, columns.get(COL_DESC_ES)),
                    text(row, columns.get(COL_TYPE)),
                    number(row, columns.get(COL_DRAWING)),
                    isEnabled(text(row, columns.get(COL_ENABLED))),
                    // +1 para que el numero coincida con lo que muestra Excel.
                    index + 1
            ));
        }
        return rows;
    }

    private Map<String, Integer> readHeader(ExcelSheet sheet, String sheetName, String entityColumn) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        ExcelRow header = sheet.row(HEADER_ROW);
        for (ExcelCell cell : header.cells()) {
            String name = cell.asString().map(value -> value.trim().toUpperCase(Locale.ROOT)).orElse("");
            if (!name.isBlank()) {
                columns.putIfAbsent(name, cell.columnIndex());
            }
        }

        for (String required : List.of(entityColumn, COL_CODE, COL_ENABLED)) {
            if (!columns.containsKey(required)) {
                throw new ExcelException("La hoja '" + sheetName + "' no tiene la columna obligatoria '"
                        + required + "'. Columnas encontradas: " + columns.keySet());
            }
        }
        return columns;
    }

    /**
     * ENABLED es lo unico que decide si una fila se carga, asi que se interpreta con
     * manga ancha: quien edita el maestro escribe indistintamente SI, S, X, TRUE o 1.
     * Cualquier otra cosa, incluida la celda vacia, cuenta como NO.
     */
    private boolean isEnabled(String raw) {
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return value.equals("SI") || value.equals("SÍ") || value.equals("S")
                || value.equals("X") || value.equals("TRUE") || value.equals("1");
    }

    private String text(ExcelRow row, Integer column) {
        if (column == null) {
            return "";
        }
        ExcelCell cell = row.cell(column);
        // Un numero de plano leido como numerico llega como Double: 6901.0.
        return cell.asString()
                .or(() -> cell.asNumber().map(this::plainNumber))
                .orElse("")
                .trim();
    }

    private Long number(ExcelRow row, Integer column) {
        if (column == null) {
            return null;
        }
        ExcelCell cell = row.cell(column);
        Optional<Double> numeric = cell.asNumber();
        if (numeric.isPresent()) {
            return numeric.get().longValue();
        }
        String value = cell.asString().orElse("").trim();
        try {
            return value.isBlank() ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String plainNumber(Double value) {
        return value == Math.rint(value) ? String.valueOf(value.longValue()) : String.valueOf(value);
    }

    /** Las dos hojas del maestro leidas de una vez. */
    public record LovMasterContent(List<LovMasterRow> lovs, List<LovMasterRow> types) {
    }
}
