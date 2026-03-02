package com.alejandro.mtoconfiguration.controller.commons;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.service.commons.CRUDService;
import org.springframework.http.ResponseEntity;

public interface DeleteController<T extends BaseDTO, E extends CRUDEntity> extends BaseController<T, E> {

    @Override
    CRUDService<T, E> getService();

    default ResponseEntity<Object> delete(T dto) {
        getService().delete(dto);
        return ResponseEntity.ok().build();
    }
}
