package com.alejandro.mtoconfiguration.controller.commons;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

public interface ReadAllController<T extends BaseDTO, E extends IEntity> extends BaseController<T, E> {


    default ResponseEntity<Object> findAll() {
        return ResponseEntity.ok(getService().findAll());
    }

    default ResponseEntity<Object> findAll(Pageable pageable) {
        return processGenericPageRequest(getService()::findAll, pageable);
    }
}
