package com.alejandro.mtoconfiguration.controller.commons;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import org.springframework.http.ResponseEntity;

public interface ReadController<T extends BaseDTO, E extends IEntity> extends BaseController<T, E> {

    default ResponseEntity<Object> getById(Long id) {
        return ResponseEntity.ok(getService().getById(id));
    }
}
