package com.alejandro.mtoconfiguration.controller.commons;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

public interface SearchController<T extends BaseDTO, E extends IEntity> extends BaseController<T, E> {

    default ResponseEntity<Object> search(@RequestBody SearchRequestDTO searchRequestDTO) {
        return ResponseEntity.ok(getService().search(searchRequestDTO));
    }


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
