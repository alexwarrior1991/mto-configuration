package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.business.commons.CRUDBusiness;
import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.utils.Utils;
import com.alejandro.mtoconfiguration.validator.commons.CRUDValidator;

import jakarta.transaction.Transactional;

import java.util.Optional;

public abstract class CRUDService<T extends BaseDTO, E extends CRUDEntity> extends BaseService<T, E> {

    @Override
    protected abstract CRUDValidator<T> getValidator();

    @Override
    protected abstract CRUDBusiness<T, E> getBusiness();

    @Transactional(rollbackOn = {ValidationException.class, Exception.class})
    public void delete(T dto) throws BaseException {
        this.softDelete(dto);
    }

    @Transactional(
            rollbackOn = {ValidationException.class, Exception.class}
    )
    public void softDelete(T dto) {
        Optional.ofNullable(dto)
                .filter(Utils::exists)
                .map(T::getId)
                .map(id -> getRepository().findById(id)
                        .orElseThrow(() -> new BaseException(getEntity().getClass().getSimpleName() + " Object not found with id " + id))
                )
                .ifPresent(entity -> {

                    Optional.ofNullable(getValidator())
                            .map(v -> v.validateBeforeDelete(dto))
                            .filter(Utils::hasErrors)
                            .ifPresent(alerts -> {
                                throw new ValidationException(alerts);
                            });

                    Optional.ofNullable(getBusiness()).ifPresent(b -> {
                        b.preDeleteDTOToEntity(dto, entity);
                        b.deleteEntity(entity); // Llama internamente a entity.delete()
                    });

                    entity.delete();

                    getRepository().saveAndFlush(entity);
                    applicationContext.getBean(this.getClass()).cacheClean(true);

                });

    }
}
