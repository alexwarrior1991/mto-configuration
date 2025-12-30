package com.alejandro.mtoconfiguration.core.exception;

import com.alejandro.mtoconfiguration.constant.RailwayInfrastructureExceptions;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.io.Serial;

@Getter
@Setter
public class RailwayInfrastructureException extends RuntimeException {

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
    @NotNull
    private String errorCode;
    @NotNull
    private HttpStatus status;

    public RailwayInfrastructureException(Throwable e) {
        this("no message set", e);
    }

    public RailwayInfrastructureException(String action, String message, String code, String category, String severity) {
        super(message);
        this.setCode(code);
        this.action = action;
        this.category = category;
        this.severity = severity;
    }

    public RailwayInfrastructureException() {
    }

    public RailwayInfrastructureException(String message, Throwable e) {
        this.setCode(message);
    }

    public RailwayInfrastructureException(String message, String code, Throwable e) {
        super(message, e);
        this.setCode(code);
    }

    public RailwayInfrastructureException(String message, String code, String errorCode, Throwable e) {
        super(message, e);
        this.setCode(code);
        this.setErrorCode(errorCode);
    }

    public RailwayInfrastructureException(String message, HttpStatus status, Throwable e) {
        super(message, e);
        this.setStatus(status);
    }

    public RailwayInfrastructureException(String code, String message) {
        super(message);
        this.code = code;
    }

    public RailwayInfrastructureException(RailwayInfrastructureExceptions ex) {
        super(ex.getMessage());
        this.code = ex.getCode();
    }

    public RailwayInfrastructureException(RailwayInfrastructureExceptions ex, Throwable cause) {
        super(ex.getMessage(), cause);
        this.code = ex.getCode();
    }
}
