package com.alejandro.mtoconfiguration.service.commons.event;

import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;

/**
 * Lo que tiene que cumplirse para poder borrar una entidad, visto en la base y no en el DTO.
 *
 * <p>{@code CRUDService} lo llama en el {@code DELETE} directo, con la entidad ya encontrada y
 * <b>antes de tocar nada</b>: si lanza, no se ha escrito nada y no sale ningun evento. Hoy hay
 * uno, {@code DeletionGuard}: lo que cuelga vivo de un maestro impide borrarlo.
 */
public interface BeforeDeleteCheck {

    void beforeDelete(CRUDEntity entity) throws BaseException;
}
