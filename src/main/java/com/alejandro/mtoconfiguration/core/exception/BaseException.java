package com.alejandro.mtoconfiguration.core.exception;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Excepción base de servicio. Transporta las alertas que la capa web convierte en la respuesta de
 * error, de modo que el detalle por campo llegue al cliente sin que el handler tenga que
 * reconstruirlo.
 */
@Slf4j
public class BaseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<Alert> errors = new ArrayList<>();

    public BaseException(String message) {
        super(message);
        addError(Alert.ofDanger(message));
    }

    public BaseException(String message, Exception e) {
        super(message, e);
        addError(Alert.ofDanger(message));
    }

    public BaseException(Alert error) {
        super(messageOf(error));
        addError(error);
    }

    public BaseException(List<Alert> errors) {
        super(messageOf(errors));

        if (CollectionUtils.isNotEmpty(errors)) {
            errors.stream().filter(Objects::nonNull).forEach(this::addError);
        }
    }

    /**
     * Alertas de la excepción, en el orden en que se produjeron y sin duplicados exactos.
     *
     * <p>La deduplicación es por código <b>y campo</b>: dos campos que incumplen la misma regla son
     * dos errores distintos y el cliente necesita los dos para pintarlos.</p>
     */
    public List<Alert> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    private void addError(Alert error) {
        if (error == null || isDuplicate(error)) {
            return;
        }

        errors.add(error);
        log.debug("Alerta registrada: {} {}", error.getMessage(), error.getFields());
    }

    private boolean isDuplicate(Alert candidate) {
        return errors.stream().anyMatch(existing ->
                Objects.equals(existing.getMessage(), candidate.getMessage())
                        && Objects.equals(existing.getFields(), candidate.getFields()));
    }

    private static String messageOf(Alert error) {
        return error != null ? error.getMessage() : null;
    }

    private static String messageOf(List<Alert> errors) {
        return CollectionUtils.isEmpty(errors) ? null : errors.get(0).getMessage();
    }
}
