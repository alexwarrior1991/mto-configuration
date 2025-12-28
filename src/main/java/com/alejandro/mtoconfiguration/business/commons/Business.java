package com.alejandro.mtoconfiguration.business.commons;

import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;

import java.io.Serializable;

public interface Business<T extends BaseDTO, E extends IEntity> extends Serializable {
    void preMapperDTOToEntity(T dto, E entity) throws BaseException;

    void preMapperEntityToDTO(E entity, T dto) throws BaseException;

    void postMapperDTOToEntity(T dto, E entity) throws BaseException;

    void postMapperEntityToDTO(E entity, T dto) throws BaseException;

    void preCancelDTOToEntity(T dto, E entity) throws BaseException;

    void preCancelEntityToDTO(E entity, T dto) throws BaseException;

    void postCancelDTOToEntity(T dto, E entity) throws BaseException;

    void postCancelEntityToDTO(E entity, T dto) throws BaseException;

    void preDeleteDTOToEntity(T dto, E entity) throws BaseException;

    void preDeleteEntityToDTO(E entity, T dto) throws BaseException;

    void postDeleteDTOToEntity(T dto, E entity) throws BaseException;

    void postDeleteEntityToDTO(E entity, T dto) throws BaseException;

    void postValidationDTOToEntity(T dto, E entity) throws BaseException;


}
