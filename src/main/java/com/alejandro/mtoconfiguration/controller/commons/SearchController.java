package com.alejandro.mtoconfiguration.controller.commons;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Utilidades de busqueda para los controladores CRUD.
 *
 * <p>El endpoint {@code search} no se declara aqui: los controladores concretos lo anotan con
 * {@code @Valid @RequestBody} y Bean Validation prohibe que un metodo que sobrescribe a otro añada
 * restricciones a sus parametros. Ver la nota de {@link SaveController}.
 */
public interface SearchController<T extends BaseDTO, E extends IEntity> extends BaseController<T, E> {

    default boolean withFilters(SearchRequestDTO searchRequestDTO) {
        if (searchRequestDTO == null || searchRequestDTO.getFilters() == null) {
            return false;
        }

        if (searchRequestDTO.getFilters().isEmpty()) {
            return false;
        }

        for (Object obj : searchRequestDTO.getFilters().values()) {
            if (obj instanceof String && StringUtils.isNotBlank((String) obj)) {
                return true;
            }

            if (obj instanceof Long) {
                return true;
            }

            if (obj instanceof List && CollectionUtils.isNotEmpty((List<?>) obj)) {
                return true;
            }
        }

        return false;
    }
}
