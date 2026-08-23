package com.alejandro.mtoconfiguration.core.excel;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Una celda ya leida de un Excel: conserva su tipo original y su valor tipado.
 *
 * <p>No convertimos todo a String a proposito: en los workbooks reales un KP es
 * numerico y un codigo con ceros a la izquierda es texto. Perder esa distincion
 * en la lectura obligaria a los parsers a adivinarla despues.
 *
 * <p>Se construye siempre con las factorias estaticas, que garantizan que el tipo
 * y el valor son coherentes.
 */
public record ExcelCell(int columnIndex, ExcelCellType type, Object rawValue, String formula) {

    public ExcelCell {

        if (columnIndex < 0) {
            throw new ExcelException("El indice de columna no puede ser negativo: " + columnIndex);
        }
        if (type == null) {
            throw new ExcelException("El tipo de celda es obligatorio");
        }

        checkValueMatchesType(type, rawValue);
    }

    public static ExcelCell ofString(int columnIndex, String value) {
        return new ExcelCell(columnIndex, ExcelCellType.STRING, value, null);
    }

    public static ExcelCell ofNumeric(int columnIndex, Double value) {
        return new ExcelCell(columnIndex, ExcelCellType.NUMERIC, value, null);
    }

    public static ExcelCell ofBoolean(int columnIndex, Boolean value) {
        return new ExcelCell(columnIndex, ExcelCellType.BOOLEAN, value, null);
    }

    public static ExcelCell ofDate(int columnIndex, LocalDateTime value) {
        return new ExcelCell(columnIndex, ExcelCellType.DATE, value, null);
    }

    /**
     * Celda con formula: guardamos el resultado ya evaluado, que es lo que
     * necesitan los parsers, y ademas el texto de la formula para diagnostico.
     */
    public static ExcelCell ofFormula(int columnIndex, Object evaluatedValue, String formula) {
        return new ExcelCell(columnIndex, ExcelCellType.FORMULA, evaluatedValue, formula);
    }

    public static ExcelCell ofBlank(int columnIndex) {
        return new ExcelCell(columnIndex, ExcelCellType.BLANK, null, null);
    }

    public static ExcelCell ofError(int columnIndex, String errorText) {
        return new ExcelCell(columnIndex, ExcelCellType.ERROR, errorText, null);
    }

    /**
     * Vacia si no hay valor o si el texto es solo espacios: para el importador,
     * una celda con " " es tan vacia como una sin contenido.
     */
    public boolean isBlank() {
        return switch (rawValue) {
            case null -> true;
            case String s -> s.isBlank();
            default -> false;
        };
    }

    public boolean isError() {
        return type == ExcelCellType.ERROR;
    }

    /**
     * Texto de la celda. Los numeros enteros se devuelven sin el ".0" que arrastra
     * POI, porque en los workbooks aparecen como identificadores ("12", no "12.0").
     */
    public Optional<String> asString() {
        if (isBlank() || isError()) {
            return Optional.empty();
        }
        return Optional.of(switch (rawValue) {
            case String s -> s.strip();
            case Double d when d == Math.floor(d) && !d.isInfinite() -> String.valueOf(d.longValue());
            case Object o -> String.valueOf(o);
        });
    }

    public Optional<Double> asNumber() {
        return Optional.ofNullable(rawValue instanceof Double d ? d : null);
    }

    public Optional<Boolean> asBoolean() {
        return Optional.ofNullable(rawValue instanceof Boolean b ? b : null);
    }

    public Optional<LocalDateTime> asDate() {
        return Optional.ofNullable(rawValue instanceof LocalDateTime d ? d : null);
    }

    public Optional<String> formulaText() {
        return Optional.ofNullable(formula);
    }


    private static void checkValueMatchesType(ExcelCellType type, Object value) {
        boolean valid = switch (type) {
            case STRING, ERROR -> value == null || value instanceof String;
            case NUMERIC -> value == null || value instanceof Double;
            case BOOLEAN -> value == null || value instanceof Boolean;
            case DATE -> value == null || value instanceof LocalDateTime;
            case BLANK -> value == null;
            // La formula puede evaluar a cualquiera de los tipos soportados.
            case FORMULA -> value == null
                    || value instanceof String
                    || value instanceof Double
                    || value instanceof Boolean
                    || value instanceof LocalDateTime;
        };
        if (!valid) {
            throw new ExcelException("Valor incompatible con el tipo de celda %s: %s"
                    .formatted(type, value.getClass().getSimpleName()));
        }
    }
}
