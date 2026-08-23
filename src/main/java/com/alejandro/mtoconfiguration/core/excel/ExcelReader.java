package com.alejandro.mtoconfiguration.core.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unica clase del proyecto que conoce Apache POI: convierte un InputStream en el
 * modelo generico {@link ExcelWorkbook}.
 *
 * <p>Aqui se resuelven de una vez las trampas del formato para que ningun parser
 * tenga que conocerlas:
 * <ul>
 *   <li>las fechas se distinguen de los numeros por su formato;</li>
 *   <li>las formulas se evaluan y se conserva ademas su texto;</li>
 *   <li>las celdas combinadas propagan su valor a todo el rango;</li>
 *   <li>las filas y columnas nunca escritas se materializan como BLANK.</li>
 * </ul>
 *
 * <p>Las celdas con error (#REF!, #DIV/0!) no abortan la lectura: se conservan
 * como celdas de tipo ERROR y sera el importador quien decida si son admisibles.
 */
public class ExcelReader {

    /**
     * Lee el workbook completo. El InputStream se cierra siempre, tambien si la
     * lectura falla.
     */
    public ExcelWorkbook read(InputStream inputStream) {
        if (inputStream == null) {
            throw new ExcelException("El fichero Excel es obligatorio");
        }

        try (InputStream source = inputStream;
             Workbook workbook = WorkbookFactory.create(source)) {

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            List<ExcelSheet> sheets = new ArrayList<>();
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                sheets.add(readSheet(workbook.getSheetAt(index), index, evaluator));
            }

            return new ExcelWorkbook(sheets);

        } catch (ExcelException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelException("No se ha podido leer el fichero Excel: " + e.getMessage(), e);
        }
    }

    private ExcelSheet readSheet(Sheet sheet, int index, FormulaEvaluator evaluator) {
        List<CellRangeAddress> mergedRegions = sheet.getMergedRegions();
        Map<Long, CellRangeAddress> mergedByCoordinate = indexMergedRegions(mergedRegions);

        int lastRow = sheet.getLastRowNum();
        for (CellRangeAddress region : mergedRegions) {
            lastRow = Math.max(lastRow, region.getLastRow());
        }

        List<ExcelRow> rows = new ArrayList<>();
        for (int rowIndex = 0; rowIndex <= lastRow; rowIndex++) {
            rows.add(readRow(sheet, rowIndex, lastColumnOf(sheet, rowIndex, mergedRegions), mergedByCoordinate, evaluator));
        }

        return new ExcelSheet(sheet.getSheetName(), index, rows);
    }

    private ExcelRow readRow(Sheet sheet, int rowIndex, int lastColumn,
                             Map<Long, CellRangeAddress> mergedByCoordinate, FormulaEvaluator evaluator) {
        if (lastColumn < 0) {
            return ExcelRow.empty(rowIndex);
        }

        Row row = sheet.getRow(rowIndex);
        List<ExcelCell> cells = new ArrayList<>();
        for (int columnIndex = 0; columnIndex <= lastColumn; columnIndex++) {
            cells.add(readCell(sheet, row, rowIndex, columnIndex, mergedByCoordinate, evaluator));
        }

        return new ExcelRow(rowIndex, cells);
    }

    /**
     * Celda de esas coordenadas. Si pertenece a un rango combinado, el valor se
     * toma de la celda superior izquierda del rango, que es la unica en la que POI
     * lo guarda.
     */
    private ExcelCell readCell(Sheet sheet, Row row, int rowIndex, int columnIndex,
                               Map<Long, CellRangeAddress> mergedByCoordinate, FormulaEvaluator evaluator) {
        Cell source = row != null ? row.getCell(columnIndex) : null;

        CellRangeAddress region = mergedByCoordinate.get(coordinate(rowIndex, columnIndex));
        if (region != null) {
            Row originRow = sheet.getRow(region.getFirstRow());
            source = originRow != null ? originRow.getCell(region.getFirstColumn()) : null;
        }

        if (source == null) {
            return ExcelCell.ofBlank(columnIndex);
        }

        return convert(source, columnIndex, evaluator);
    }

    private ExcelCell convert(Cell cell, int columnIndex, FormulaEvaluator evaluator) {
        return switch (cell.getCellType()) {
            case STRING -> ExcelCell.ofString(columnIndex, cell.getStringCellValue());
            case NUMERIC -> numericCell(cell, columnIndex);
            case BOOLEAN -> ExcelCell.ofBoolean(columnIndex, cell.getBooleanCellValue());
            case ERROR -> ExcelCell.ofError(columnIndex, errorText(cell.getErrorCellValue()));
            case FORMULA -> formulaCell(cell, columnIndex, evaluator);
            case BLANK, _NONE -> ExcelCell.ofBlank(columnIndex);
        };
    }

    /**
     * POI guarda las fechas como numeros: solo el formato de la celda permite
     * saber que un 45000.0 es en realidad una fecha.
     */
    private ExcelCell numericCell(Cell cell, int columnIndex) {
        if (DateUtil.isCellDateFormatted(cell)) {
            return ExcelCell.ofDate(columnIndex, cell.getLocalDateTimeCellValue());
        }
        return ExcelCell.ofNumeric(columnIndex, cell.getNumericCellValue());
    }

    /**
     * El parser necesita el resultado, no la formula; el texto de la formula se
     * conserva solo para poder diagnosticar. Una formula que evalua a error se
     * trata como cualquier otra celda con error.
     */
    private ExcelCell formulaCell(Cell cell, int columnIndex, FormulaEvaluator evaluator) {
        String formula = cell.getCellFormula();

        CellValue value;
        try {
            value = evaluator.evaluate(cell);
        } catch (RuntimeException e) {
            // Funciones que POI no sabe evaluar: no se pierde la formula, pero no hay valor.
            return ExcelCell.ofFormula(columnIndex, null, formula);
        }

        if (value == null) {
            return ExcelCell.ofFormula(columnIndex, null, formula);
        }

        return switch (value.getCellType()) {
            case STRING -> ExcelCell.ofFormula(columnIndex, value.getStringValue(), formula);
            case BOOLEAN -> ExcelCell.ofFormula(columnIndex, value.getBooleanValue(), formula);
            case NUMERIC -> ExcelCell.ofFormula(columnIndex, evaluatedNumber(cell, value), formula);
            case ERROR -> ExcelCell.ofError(columnIndex, errorText(value.getErrorValue()));
            default -> ExcelCell.ofFormula(columnIndex, null, formula);
        };
    }

    private Object evaluatedNumber(Cell cell, CellValue value) {
        if (DateUtil.isCellDateFormatted(cell)) {
            return LocalDateTime.ofInstant(
                    DateUtil.getJavaDate(value.getNumberValue()).toInstant(),
                    java.util.TimeZone.getDefault().toZoneId());
        }
        return value.getNumberValue();
    }

    private String errorText(byte errorCode) {
        try {
            return FormulaError.forInt(errorCode).getString();
        } catch (IllegalArgumentException e) {
            return "#ERROR!";
        }
    }

    /**
     * Ultima columna a leer de la fila: la ultima escrita fisicamente, ampliada
     * hasta cubrir los rangos combinados que la atraviesan.
     */
    private int lastColumnOf(Sheet sheet, int rowIndex, List<CellRangeAddress> mergedRegions) {
        Row row = sheet.getRow(rowIndex);
        int lastColumn = row != null ? row.getLastCellNum() - 1 : -1;

        for (CellRangeAddress region : mergedRegions) {
            if (rowIndex >= region.getFirstRow() && rowIndex <= region.getLastRow()) {
                lastColumn = Math.max(lastColumn, region.getLastColumn());
            }
        }

        return lastColumn;
    }

    private Map<Long, CellRangeAddress> indexMergedRegions(List<CellRangeAddress> mergedRegions) {
        Map<Long, CellRangeAddress> indexed = new HashMap<>();
        for (CellRangeAddress region : mergedRegions) {
            for (int row = region.getFirstRow(); row <= region.getLastRow(); row++) {
                for (int column = region.getFirstColumn(); column <= region.getLastColumn(); column++) {
                    indexed.putIfAbsent(coordinate(row, column), region);
                }
            }
        }
        return indexed;
    }

    private static long coordinate(int rowIndex, int columnIndex) {
        return ((long) rowIndex << 32) | columnIndex;
    }
}
