package com.alejandro.mtoconfiguration.core.exception;

import java.io.Serial;

public class ConcurrencyException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConcurrencyException(String message) {
        super(message);
    }
}
