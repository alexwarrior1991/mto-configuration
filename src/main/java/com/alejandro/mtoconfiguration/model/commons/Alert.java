package com.alejandro.mtoconfiguration.model.commons;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Setter
@NoArgsConstructor
public class Alert implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    private String message;
    @Getter
    private String details;
    @Getter
    private AlertLevel level;
    @Getter
    private String type;
    private List<String> fields;

    @Getter
    private Object excelErrors;

    public Alert ofSuccess(String message, String... fields) {
        return new Alert(AlertLevel.SUCCESS, message, fields);
    }

    public static Alert ofSuccess(String message, String details, Collection<String> fields) {
        return new Alert(AlertLevel.SUCCESS, message, details, fields);
    }

    public static Alert ofInfo(String message, String... fields) {
        return new Alert(AlertLevel.INFO, message, fields);
    }

    public static Alert ofInfo(String message, String details, Collection<String> fields) {
        return new Alert(AlertLevel.INFO, message, details, fields);
    }

    public static Alert ofWarning(String message, String... fields) {
        return new Alert(AlertLevel.WARNING, message, fields);
    }

    public static Alert ofWarning(String message, String details, Collection<String> fields) {
        return new Alert(AlertLevel.WARNING, message, details, fields);
    }

    public static Alert ofDanger(String message, String... fields) {
        return new Alert(AlertLevel.DANGER, message, fields);
    }

    public static Alert ofDanger(String message, Exception exception) {
        return new Alert(AlertLevel.DANGER, message, exception.getMessage(), Collections.emptyList());
    }

    public static Alert ofDanger(String message, String details, Collection<String> fields) {
        return new Alert(AlertLevel.DANGER, message, details, fields);
    }

    public static Alert ofDanger(Object excelError) {
        Alert alert = new Alert();
        alert.setLevel(AlertLevel.DANGER);
        alert.setExcelErrors(excelError);
        return alert;
    }

    private Alert(AlertLevel level, String message, String... fields) {
        this.message = message;
        this.level = level;
        this.fields = new ArrayList<>();

        if (fields != null && fields.length > 0) {
            this.fields.addAll(Arrays.stream(fields).toList());
        }
    }

    private Alert(AlertLevel level, String message, String details, Collection<String> fields) {
        this.message = message;
        this.details = details;
        this.level = level;
        this.fields = fields != null ? new ArrayList<>(fields) : new ArrayList<>();
    }

    public List<String> getFields() {
        if (fields == null) {
            fields = new ArrayList<>();
        }
        return fields;
    }

    public Alert addDetails(String details) {
        this.details = details;
        return this;
    }

    public Alert addStackTrace(Throwable e) {
        this.details = ExceptionUtils.getStackTrace(e);
        return this;
    }

}
