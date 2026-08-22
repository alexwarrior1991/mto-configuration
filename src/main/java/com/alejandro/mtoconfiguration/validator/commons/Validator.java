package com.alejandro.mtoconfiguration.validator.commons;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;

import java.util.Collections;
import java.util.List;

/**
 * Contrato de validación de un DTO antes de cada operación del servicio.
 *
 * <p>Las implementaciones deben ser <b>stateless</b>: las alertas se construyen y devuelven en cada
 * llamada, nunca se acumulan en el propio validador. Eso permite que los validadores sean beans
 * singleton y que funcionen igual dentro y fuera de una petición HTTP (por ejemplo desde
 * {@code BaseAsyncService}, que ejecuta en un hilo del pool).</p>
 */
public interface Validator<T extends BaseDTO> {

    List<Alert> validateBeforeSave(T dto);

    default List<Alert> validateBeforeUpdate(T dto) {
        return validateBeforeSave(dto);
    }

    default List<Alert> validateBeforeCancel(T dto) {
        return Collections.emptyList();
    }

    default List<Alert> validateBeforeSearch(SearchRequestDTO dto) {
        return Collections.emptyList();
    }

    default List<Alert> validateBeforeBulkSave(List<T> dto) {
        return Collections.emptyList();
    }

    default List<Alert> validateBeforeBulkUpdate(List<T> dto) {
        return Collections.emptyList();
    }
}
