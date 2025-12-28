package com.alejandro.mtoconfiguration.validator.commons;

public interface ErrorCatalogEntry {

    ErrorCode errorCode();

    default String code() {
        return errorCode().code();
    }
}
