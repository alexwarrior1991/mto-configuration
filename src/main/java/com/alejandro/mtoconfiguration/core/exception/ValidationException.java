package com.alejandro.mtoconfiguration.core.exception;

import com.alejandro.mtoconfiguration.model.commons.Alert;

import java.io.Serial;
import java.util.List;

public class ValidationException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(List<Alert> errors) {
        super(errors);
    }

    public ValidationException(Alert error) {
        super(error);
    }
}
