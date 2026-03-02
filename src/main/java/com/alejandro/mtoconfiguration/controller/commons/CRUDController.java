package com.alejandro.mtoconfiguration.controller.commons;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import org.springframework.web.bind.annotation.RestController;

@RestController
public abstract class CRUDController<T extends BaseDTO, E extends CRUDEntity>
        implements ReadController<T, E>, SaveController<T, E>, DeleteController<T, E>, SearchController<T, E> {
}
