package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.business.commons.CRUDBusiness;
import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.service.commons.event.BeforeDeleteCheck;
import com.alejandro.mtoconfiguration.utils.Utils;
import com.alejandro.mtoconfiguration.validator.commons.CRUDValidator;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

public abstract class CRUDService<T extends BaseDTO, E extends CRUDEntity> extends BaseService<T, E> {

    @Autowired(required = false)
    private List<BeforeDeleteCheck> beforeDeleteChecks;

    @Override
    protected abstract CRUDValidator<T> getValidator();

    @Override
    protected abstract CRUDBusiness<T, E> getBusiness();

    @Transactional(rollbackOn = {ValidationException.class, Exception.class})
    public void delete(T dto) throws BaseException {
        softDelete(dto);
    }

    protected void softDelete(T dto) {
        trackingNestedChanges(() -> {
            Optional.ofNullable(dto)
                    .filter(Utils::exists)
                    .map(T::getId)
                    .map(id -> getRepository().findById(id)
                            .orElseThrow(() -> notFound(id))
                    )
                    .ifPresent(entity -> {
                        Optional.ofNullable(getValidator())
                                .map(v -> v.validateBeforeDelete(dto))
                                .filter(Utils::hasErrors)
                                .ifPresent(alerts -> {
                                    throw new ValidationException(alerts);
                                });

                        // Antes de tocar nada: lo que cuelga vivo de la entidad impide borrarla (409).
                        checkBeforeDelete(entity);

                        Optional.ofNullable(getBusiness())
                                .ifPresent(b -> {
                                    b.preDeleteDTOToEntity(dto, entity);
                                    b.deleteEntity(entity); // Llama internamente a entity.delete()
                                });

                        // Con sus hijos propios (las mensulas de un perfil con sus brazos, el brazo de
                        // una mensula, las agujas de un aislador): no existen sin ella, y dejarlos
                        // vivos era dejarlos huerfanos. Lo demas que cuelga ya lo ha impedido el 409.
                        entity.deleteWithOwnChildren();

                        E savedEntity = getRepository().saveAndFlush(entity);

                        checkWrittenEntities();
                        publishEntityDeletedEvent(savedEntity);
                        publishNestedChanges();

                        publishCacheEvictionEvent();
                    });

            return null;
        });
    }

    /**
     * Las comprobaciones de un borrado que miran la base ({@link BeforeDeleteCheck}): hoy, que de la
     * entidad no cuelgue nada vivo. Va antes de escribir, asi que un rechazo no deja nada.
     */
    protected void checkBeforeDelete(E entity) {
        if (beforeDeleteChecks != null) {
            beforeDeleteChecks.forEach(check -> check.beforeDelete(entity));
        }
    }
}
