package com.alejandro.mtoconfiguration.core.excel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Una fila ya leida de un Excel: su indice y sus celdas, en orden de columna.
 *
 * <p>Las filas reales no tienen todas la misma longitud y pueden dejar huecos:
 * POI no crea celda alguna para una columna nunca escrita. Para que los parsers
 * no repitan la misma comprobacion defensiva en cada columna, pedir una columna
 * inexistente devuelve una celda BLANK, nunca null ni una excepcion de indice.
 */
public record ExcelRow(int rowIndex, List<ExcelCell> cells) {

    public ExcelRow {

        if (rowIndex < 0) {
            throw new ExcelException("El indice de fila no puede ser negativo: " + rowIndex);
        }
        if (cells == null) {
            throw new ExcelException("La lista de celdas es obligatoria");
        }
        // contains(null) no vale: las listas inmutables lo rechazan con NPE.
        if (cells.stream().anyMatch(Objects::isNull)) {
            throw new ExcelException("La fila %d contiene celdas nulas".formatted(rowIndex));
        }

        cells = List.copyOf(cells);
    }

    public static ExcelRow of(int rowIndex, ExcelCell... cells) {
        return new ExcelRow(rowIndex, List.of(cells));
    }

    public static ExcelRow empty(int rowIndex) {
        return new ExcelRow(rowIndex, List.of());
    }

    /**
     * Celda de la columna indicada. Nunca devuelve null: si la fila es mas corta o
     * la columna es un hueco, devuelve una celda BLANK de esa misma columna.
     *
     * <p>Se busca por el columnIndex real de la celda y no por su posicion en la
     * lista, porque en cuanto hay un hueco ambas cosas dejan de coincidir. El
     * acceso directo cubre el caso habitual (fila densa) y solo se recorre la
     * lista cuando hay huecos.
     */
    public ExcelCell cell(int columnIndex) {
        if (columnIndex < 0) {
            throw new ExcelException("El indice de columna no puede ser negativo: " + columnIndex);
        }

        if (columnIndex < cells.size()) {
            ExcelCell candidate = cells.get(columnIndex);
            if (candidate.columnIndex() == columnIndex) {
                return candidate;
            }
        }

        return cells.stream()
                .filter(cell -> cell.columnIndex() == columnIndex)
                .findFirst()
                .orElseGet(() -> ExcelCell.ofBlank(columnIndex));
    }

    public Optional<String> string(int columnIndex) {
        return cell(columnIndex).asString();
    }

    public Optional<Double> number(int columnIndex) {
        return cell(columnIndex).asNumber();
    }

    public Optional<Boolean> bool(int columnIndex) {
        return cell(columnIndex).asBoolean();
    }

    public Optional<LocalDateTime> date(int columnIndex) {
        return cell(columnIndex).asDate();
    }

    /**
     * Fila sin informacion. Los workbooks estan llenos de filas separadoras y de
     * filas con solo espacios: el parser tiene que poder saltarlas sin analizarlas.
     */
    public boolean isEmpty() {
        return cells.stream().allMatch(ExcelCell::isBlank);
    }

    public int size() {
        return cells.size();
    }
}
