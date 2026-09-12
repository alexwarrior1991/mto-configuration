package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports;

/** Una fila de la hoja STATIONS. La clave natural es {@code (paquete, nombre)}. */
public record StationMasterRow(String executionPackage, String name, int sourceRow) {
}
