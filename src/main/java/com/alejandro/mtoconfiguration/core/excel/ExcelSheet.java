package com.alejandro.mtoconfiguration.core.excel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Una hoja ya leida de un Excel: su nombre, su indice en el workbook y sus filas.
 *
 * <p>Igual que ocurre con las columnas dentro de una fila, POI no crea fila alguna
 * para una fila nunca escrita. Pedir una fila inexistente devuelve una fila vacia,
 * nunca null ni una excepcion de indice.
 *
 * <p>Las celdas combinadas llegan aqui con el valor ya propagado a todo el rango:
 * esa resolucion la hace el reader, que es quien conoce los rangos de POI. La hoja
 * es una estructura pura y se puede construir en los tests sin ningun fichero.
 */
public record ExcelSheet(String name, int index, List<ExcelRow> rows) {

    public ExcelSheet {

        if (name == null || name.isBlank()) {
            throw new ExcelException("El nombre de la hoja es obligatorio");
        }
        if (index < 0) {
            throw new ExcelException("El indice de hoja no puede ser negativo: " + index);
        }
        if (rows == null) {
            throw new ExcelException("La lista de filas es obligatoria");
        }
        // contains(null) no vale: las listas inmutables lo rechazan con NPE.
        if (rows.stream().anyMatch(Objects::isNull)) {
            throw new ExcelException("La hoja %s contiene filas nulas".formatted(name));
        }

        rows = List.copyOf(rows);
    }

    public static ExcelSheet of(String name, int index, ExcelRow... rows) {
        return new ExcelSheet(name, index, List.of(rows));
    }

    public static ExcelSheet empty(String name, int index) {
        return new ExcelSheet(name, index, List.of());
    }

    /**
     * Fila del indice indicado. Nunca devuelve null: si la hoja no llega hasta ahi
     * o esa fila nunca se escribio, devuelve una fila vacia con ese mismo indice.
     *
     * <p>Se busca por el rowIndex real y no por la posicion en la lista, porque en
     * cuanto hay un hueco ambas cosas dejan de coincidir. El acceso directo cubre
     * el caso habitual (hoja densa) y solo se recorre la lista cuando hay huecos.
     */
    public ExcelRow row(int rowIndex) {
        if (rowIndex < 0) {
            throw new ExcelException("El indice de fila no puede ser negativo: " + rowIndex);
        }

        if (rowIndex < rows.size()) {
            ExcelRow candidate = rows.get(rowIndex);
            if (candidate.rowIndex() == rowIndex) {
                return candidate;
            }
        }

        return rows.stream()
                .filter(row -> row.rowIndex() == rowIndex)
                .findFirst()
                .orElseGet(() -> ExcelRow.empty(rowIndex));
    }

    /**
     * Acceso directo a una celda por coordenadas: es la forma en que los parsers
     * leen la mayor parte del contenido.
     */
    public ExcelCell cell(int rowIndex, int columnIndex) {
        return row(rowIndex).cell(columnIndex);
    }

    public int rowCount() {
        return rows.size();
    }

    /**
     * Filas con contenido, en orden. Las hojas reales estan llenas de separadoras
     * que el parser no necesita ver.
     */
    public List<ExcelRow> nonEmptyRows() {
        return rows.stream()
                .filter(row -> !row.isEmpty())
                .toList();
    }

    /**
     * Hoja sin informacion. Hay hojas de plantilla vacias que hay que ignorar sin
     * que la importacion falle.
     */
    public boolean isEmpty() {
        return rows.stream().allMatch(ExcelRow::isEmpty);
    }

    /**
     * Primera fila que cumple la condicion. Sirve para localizar la cabecera sin
     * que cada parser reescriba el mismo bucle.
     */
    public Optional<ExcelRow> findFirstRow(Predicate<ExcelRow> condition) {
        if (condition == null) {
            throw new ExcelException("La condicion de busqueda es obligatoria");
        }
        return rows.stream().filter(condition).findFirst();
    }
}
