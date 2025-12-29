package com.alejandro.mtoconfiguration.core.exception;

import jakarta.validation.constraints.NotNull;

import java.io.Serial;

public class GenericException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 4109621984563570566L;

    @NotNull
    private final String code;

    public GenericException(String message, Throwable e) {
        this(message, "0", e);
    }

    public GenericException(String message, String code, Throwable e) {
        super(message, e);
        this.code = code;
    }

    public @NotNull String getCode() {
        return code;
    }
}
