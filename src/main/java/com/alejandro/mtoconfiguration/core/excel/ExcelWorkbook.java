package com.alejandro.mtoconfiguration.core.excel;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Un workbook ya leido: sus hojas, en el orden en que aparecen en el fichero.
 *
 * <p>Es la raiz del modelo generico. No sabe nada del dominio: mas adelante el
 * importador decidira que un workbook representa un EP y que cada hoja representa
 * una Track, pero esa interpretacion no vive aqui.
 *
 * <p>El acceso por nombre no distingue mayusculas ni espacios sobrantes, porque en
 * los ficheros reales el mismo nombre aparece escrito de formas distintas y ese
 * detalle no deberia hacer fallar una importacion.
 */
public record ExcelWorkbook(List<ExcelSheet> sheets) {

    public ExcelWorkbook {

        if (sheets == null) {
            throw new ExcelException("La lista de hojas es obligatoria");
        }
        // contains(null) no vale: las listas inmutables lo rechazan con NPE.
        if (sheets.stream().anyMatch(Objects::isNull)) {
            throw new ExcelException("El workbook contiene hojas nulas");
        }
        checkNamesAreUnique(sheets);

        sheets = List.copyOf(sheets);
    }

    public static ExcelWorkbook of(ExcelSheet... sheets) {
        return new ExcelWorkbook(List.of(sheets));
    }

    public static ExcelWorkbook empty() {
        return new ExcelWorkbook(List.of());
    }

    /**
     * Hoja con ese nombre, si existe. Devuelve Optional y no una hoja vacia como
     * hacen row() o cell(): que falte una hoja entera no es una irregularidad del
     * formato, es una decision que el importador tiene que tomar explicitamente.
     */
    public Optional<ExcelSheet> sheet(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        return sheets.stream()
                .filter(sheet -> normalize(sheet.name()).equals(normalize(name)))
                .findFirst();
    }

    /**
     * Hoja de la posicion indicada. Se busca por el indice real de la hoja, que es
     * el que el reader copia del fichero.
     */
    public Optional<ExcelSheet> sheet(int index) {
        if (index < 0) {
            throw new ExcelException("El indice de hoja no puede ser negativo: " + index);
        }

        if (index < sheets.size()) {
            ExcelSheet candidate = sheets.get(index);
            if (candidate.index() == index) {
                return Optional.of(candidate);
            }
        }

        return sheets.stream()
                .filter(sheet -> sheet.index() == index)
                .findFirst();
    }

    /**
     * Nombres de las hojas tal cual estan en el fichero, en orden. Sirve para
     * informar al usuario de que se ha encontrado antes de importar nada.
     */
    public List<String> sheetNames() {
        return sheets.stream()
                .map(ExcelSheet::name)
                .toList();
    }

    /**
     * Hojas con contenido. Las plantillas vacias no deben llegar a los parsers.
     */
    public List<ExcelSheet> nonEmptySheets() {
        return sheets.stream()
                .filter(sheet -> !sheet.isEmpty())
                .toList();
    }

    public int sheetCount() {
        return sheets.size();
    }

    /**
     * Workbook sin informacion: o no tiene hojas o todas estan vacias.
     */
    public boolean isEmpty() {
        return sheets.stream().allMatch(ExcelSheet::isEmpty);
    }

    /**
     * Excel no admite dos hojas con el mismo nombre; si aparecen, es que el reader
     * las ha construido mal y el acceso por nombre seria ambiguo.
     */
    private static void checkNamesAreUnique(List<ExcelSheet> sheets) {
        Set<String> names = new HashSet<>();
        sheets.stream()
                .map(sheet -> normalize(sheet.name()))
                .filter(name -> !names.add(name))
                .findFirst()
                .ifPresent(duplicated -> {
                    throw new ExcelException("El workbook contiene hojas con nombre duplicado: " + duplicated);
                });
    }

    private static String normalize(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
