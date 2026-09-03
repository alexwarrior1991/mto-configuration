package com.alejandro.mtoconfiguration.model.synchronous.lov.imports;

/**
 * Una fila de la hoja {@code LOVS} del catalogo maestro {@code lov-master.xlsx}.
 *
 * <p>El maestro lo genera {@code data/tools/build_lov_master.py} consolidando los
 * workbooks de Execution Package, que son heterogeneos y sucios. Toda esa suciedad
 * (alias ES/EN, celdas multivalor, hojas de un millon de filas) se resuelve alli,
 * de modo que aqui llega un formato plano y estable.
 *
 * @param entity       entidad LOV destino, p. ej. {@code Foundation} o {@code Sectioning}
 * @param code         codigo del valor; es la clave natural del upsert
 * @param descriptionEn descripcion en ingles, puede venir vacia
 * @param descriptionEs descripcion en espanol, puede venir vacia
 * @param type         codigo del {@code *Type} asociado; solo para Foundation,
 *                     Portal y AnchorageFoundation, vacio en el resto
 * @param drawingNumber numero de plano, nulo si el origen no traia uno numerico
 * @param enabled      unico campo que decide si la fila se carga; lo marca a mano
 *                     ingenieria sobre el Excel
 * @param sourceRow    fila del Excel de la que procede, para poder senalarla en los
 *                     errores del informe
 */
public record LovMasterRow(
        String entity,
        String code,
        String descriptionEn,
        String descriptionEs,
        String type,
        Long drawingNumber,
        boolean enabled,
        int sourceRow
) {

    /**
     * Descripcion a persistir: se prefiere la inglesa porque es la que usa el
     * {@code Legend}, que es la fuente curada; la espanola queda de respaldo.
     */
    public String description() {
        if (descriptionEn != null && !descriptionEn.isBlank()) {
            return descriptionEn;
        }
        return descriptionEs != null && !descriptionEs.isBlank() ? descriptionEs : code;
    }

    public boolean hasType() {
        return type != null && !type.isBlank();
    }
}
