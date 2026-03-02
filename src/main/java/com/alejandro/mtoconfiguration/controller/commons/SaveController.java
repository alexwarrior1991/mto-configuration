package com.alejandro.mtoconfiguration.controller.commons;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface SaveController<T extends BaseDTO, E extends IEntity> extends BaseController<T, E> {

    default ResponseEntity<Object> create(T dto) {
        return processRequestWithValidation(getService()::create, dto);
    }

    default ResponseEntity<Object> bulkCreate(List<T> dtos) {
        return processBulkRequestWithValidation(getService()::bulkCreate, dtos);
    }

    default ResponseEntity<Object> update(T dto) {
        return processRequestWithValidation(getService()::update, dto);
    }

}
