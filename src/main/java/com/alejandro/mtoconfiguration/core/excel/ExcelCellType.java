package com.alejandro.mtoconfiguration.core.excel;

/**
 * Tipos de celda que sabemos representar. Es un espejo simplificado de los tipos
 * de Apache POI, para que el resto de la aplicacion no dependa de POI.
 */
public enum ExcelCellType {
    STRING,
    NUMERIC,
    BOOLEAN,
    DATE,
    FORMULA,
    BLANK,
    ERROR
}