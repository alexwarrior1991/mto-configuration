package com.alejandro.mtoconfiguration.controller.commons;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import org.springframework.http.ResponseEntity;

public interface CancelController<T extends BaseDTO, E extends IEntity> extends BaseController<T, E> {

    default ResponseEntity<Object> cancel(T dto) {
        return processRequestWithValidation(getService()::cancel, dto);
    }
}
