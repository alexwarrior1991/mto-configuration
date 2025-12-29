package com.alejandro.mtoconfiguration.business.commons;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import org.springframework.stereotype.Component;

import java.io.Serial;

@Component
public class CRUDBusiness<T extends BaseDTO, E extends CRUDEntity> extends BaseBusiness<T, E> {

    @Serial
    private static final long serialVersionUID = 1L;

    public void deleteEntity(E entity) {
        entity.delete();
    }
}
