package com.alejandro.mtoconfiguration.validator.commons;

import java.util.Optional;

@FunctionalInterface
public interface ErrorCodeResolver {
    Optional<ErrorCode> findByCode(String code);
}
