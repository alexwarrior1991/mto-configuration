package com.alejandro.mtoconfiguration.core.excel;

import com.alejandro.mtoconfiguration.core.exception.BaseException;

import java.io.Serial;

/**
 * Error de infraestructura al leer o interpretar un Excel. Extiende BaseException
 * para que RestExceptionHandler la trate como el resto de errores de la aplicacion.
 */
public class ExcelException extends BaseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ExcelException(String message) {
        super(message);
    }

    public ExcelException(String message, Exception cause) {
        super(message, cause);
    }
}
