package com.alejandro.mtoconfiguration.business.commons;

import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import org.springframework.stereotype.Component;

import java.io.Serial;

@Component
public class BaseBusiness<T extends BaseDTO, E extends BaseEntity> implements Business<T, E> {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public void preMapperDTOToEntity(T dto, E entity) throws BaseException {

    }

    @Override
    public void preMapperEntityToDTO(E entity, T dto) throws BaseException {

    }

    @Override
    public void postMapperDTOToEntity(T dto, E entity) throws BaseException {

    }

    @Override
    public void postMapperEntityToDTO(E entity, T dto) throws BaseException {

    }

    @Override
    public void preCancelDTOToEntity(T dto, E entity) throws BaseException {

    }

    @Override
    public void preCancelEntityToDTO(E entity, T dto) throws BaseException {

    }

    @Override
    public void postCancelDTOToEntity(T dto, E entity) throws BaseException {

    }

    @Override
    public void postCancelEntityToDTO(E entity, T dto) throws BaseException {

    }

    @Override
    public void preDeleteDTOToEntity(T dto, E entity) throws BaseException {

    }

    @Override
    public void preDeleteEntityToDTO(E entity, T dto) throws BaseException {

    }

    @Override
    public void postDeleteDTOToEntity(T dto, E entity) throws BaseException {

    }

    @Override
    public void postDeleteEntityToDTO(E entity, T dto) throws BaseException {

    }

    @Override
    public void postValidationDTOToEntity(T dto, E entity) throws BaseException {

    }
}
