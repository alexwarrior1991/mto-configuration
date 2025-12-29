package com.alejandro.mtoconfiguration.core.exception;

import com.alejandro.mtoconfiguration.model.commons.Alert;

import java.io.Serial;

public class NotFoundException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(Alert error) {
        super(error);
    }
}
