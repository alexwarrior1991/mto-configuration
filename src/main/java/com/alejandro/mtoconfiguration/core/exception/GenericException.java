package com.alejandro.mtoconfiguration.core.exception;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

@Getter
@Setter
public class GenericException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 4109621984563570566L;

    @NotNull
    private String action;
    @NotNull
    private String code;
    @NotNull
    private String category;
    @NotNull
    private String severity;

    public GenericException(Throwable e) {
        this("no message set", e);
    }

    public GenericException(String action, String message, String code, String category, String severity) {
        super(message);
        this.setCode(code);
        this.action = action;
        this.category = category;
        this.severity = severity;
    }

    public GenericException() {
    }

    public GenericException(String message, Throwable e) {
        this(message, "0", e);
    }

    public GenericException(String message, String code, Throwable e) {
        super(message, e);
        this.setCode(code);
    }
}
