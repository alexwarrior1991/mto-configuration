package com.alejandro.mtoconfiguration.business.commons;

import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.entity.commons.BaseDocumentEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDocumentDTO;

public interface DocumentBusiness<T extends BaseDocumentDTO, E extends BaseDocumentEntity> extends Business<T, E> {

    void preCancelDTOToEntity(T dto, E entity) throws BaseException;

    void preCancelEntityToDTO(E entity, T dto) throws BaseException;

    void postCancelDTOToEntity(T dto, E entity) throws BaseException;

    void postCancelEntityToDTO(E entity, T dto) throws BaseException;

    void preAnnulDTOToEntity(T dto, E entity) throws BaseException;

    void preAnnulEntityToDTO(E entity, T dto) throws BaseException;

    void postAnnulDTOToEntity(T dto, E entity) throws BaseException;

    void postAnnulEntityToDTO(E entity, T dto) throws BaseException;

    void preSubmitDTOToEntity(T dto, E entity) throws BaseException;

    void preSubmitEntityToDTO(E entity, T dto) throws BaseException;

    void postSubmitDTOToEntity(T dto, E entity) throws BaseException;

    void postSubmitEntityToDTO(E entity, T dto) throws BaseException;
}
