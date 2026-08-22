package com.alejandro.mtoconfiguration.validator.commons;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.utils.ValidatorUtils;

import java.util.List;

/**
 * Base común de todos los validadores.
 *
 * <p>No guarda estado: las alertas viajan siempre como parámetro y {@link ValidatorUtils} es la
 * única caja de herramientas de validación. Cualquier helper que se necesite aquí debe recibir la
 * lista destino, nunca acumularla en un campo del validador.</p>
 */
public abstract class GenericValidator<T extends BaseDTO> implements Validator<T> {

    /**
     * Nombre lógico de la entidad, usado como nombre de campo cuando el DTO completo es nulo.
     */
    protected abstract String getEntityName();

    /**
     * Atajo para encadenar validaciones sobre la lista de alertas de la operación en curso.
     */
    protected ValidatorUtils check(List<Alert> alerts) {
        return ValidatorUtils.of(alerts);
    }
}
