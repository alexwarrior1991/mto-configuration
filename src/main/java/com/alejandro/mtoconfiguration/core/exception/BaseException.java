package com.alejandro.mtoconfiguration.core.exception;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.io.Serial;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

@Slf4j
public class BaseException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Logger logger = Logger.getLogger(this.getClass().getName());

    private List<Alert> errors;

    public BaseException(String message) {
        super(message);
        errors = new ArrayList<>();

        this.getErrors().add(Alert.ofDanger(message));
        log.info(message);
    }

    public BaseException(String message, Exception e) {
        super(message, e);
        errors = new ArrayList<>();

        this.getErrors().add(Alert.ofDanger(message));
        log.info(message);
    }

    public BaseException(Alert error) {
        super();
        errors = new ArrayList<>();

        if (error != null) {
            errors.add(error);
            log.info(error.getMessage());
        }
    }

    public BaseException(List<Alert> errors) {
        super();
        errors = new ArrayList<>();

        if (CollectionUtils.isNotEmpty(errors)) {
            this.errors.addAll(errors);
            errors.forEach(error -> log.info(error.getMessage()));
        }
    }

    public List<Alert> getErrors() {
        boolean excelAlerts = errors.stream().anyMatch(alert -> alert.getExcelErrors() != null);
        if (excelAlerts) {
            return errors;
        }

        errors = errors.stream()
                .filter(this.distinctByMessage(Alert::getMessage))
                .toList();

        return errors;
    }

    private <T> Predicate<T> distinctByMessage(Function<? super T, ?> messageFunction) {
        Map<Object, Boolean> messageExists = new HashMap<>();
        return (m) -> {
            return messageExists.putIfAbsent(Objects.requireNonNull(messageFunction.apply(m)), Boolean.TRUE) == null;
        };
    }
}
