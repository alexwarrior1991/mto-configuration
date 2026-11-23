package com.alejandro.mtoconfiguration.core.exception;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;

import java.io.Serial;
import java.util.List;

/**
 * Lo que se quiere borrar tiene cosas vivas que cuelgan de el, y borrarlo las dejaria apuntando a
 * un maestro que ya no esta.
 *
 * <p>Sale como 409 {@code BUS-002}, el codigo de «valor repetido o entrada en uso» que las dos
 * aplicaciones web ya leen, pero con su propio detalle: que cuelga y cuanto ({@link #getDetail()}),
 * y lo mismo en claro para quien quiera decirlo con sus palabras ({@link #getDependents()}).
 */
public class EntityInUseException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Una clase de cosas que cuelgan ({@code profiles}, {@code disconnectors}...) y cuantas son. */
    public record Dependent(String type, long count) {
    }

    private final String detail;
    private final transient List<Dependent> dependents;

    private final String field;

    public EntityInUseException(String detail, List<Dependent> dependents) {
        this(detail, dependents, null);
    }

    /**
     * @param field la coleccion del cuerpo de la que se quito lo que no se puede borrar
     *              ({@code tracks}), cuando el borrado viene del {@code PUT} de su padre
     */
    public EntityInUseException(String detail, List<Dependent> dependents, String field) {
        super(Alert.ofDanger(ErrorCodes.DUPLICATED_RESOURCE));
        this.detail = detail;
        this.dependents = List.copyOf(dependents);
        this.field = field;
    }

    public String getField() {
        return field;
    }

    public String getDetail() {
        return detail;
    }

    public List<Dependent> getDependents() {
        return dependents;
    }

    @Override
    public String getMessage() {
        return detail;
    }
}
