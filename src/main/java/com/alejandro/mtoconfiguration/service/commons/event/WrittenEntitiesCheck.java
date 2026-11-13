package com.alejandro.mtoconfiguration.service.commons.event;

import com.alejandro.mtoconfiguration.core.exception.BaseException;

import java.util.List;

/**
 * Una regla que se comprueba sobre lo que una escritura de {@code BaseService} ha escrito de verdad,
 * <b>despues del flush</b> y antes de publicar ningun evento, dentro de su transaccion.
 *
 * <p>Es el sitio de las reglas que no caben en un validador ni en un indice: las que dependen de
 * otras filas y tienen que valer igual por todos los caminos. Un validador ve un DTO, y el hijo que
 * viaja dentro del {@code PUT} de su padre no pasa por el servicio de ese hijo; despues del flush,
 * en cambio, estan en la base la raiz y todos sus hijos, vengan de donde vengan, y
 * {@link EntityChangeTracker#written()} dice cuales.
 *
 * <p>Lanzar deshace la escritura entera: {@code BaseService} escribe con
 * {@code rollbackOn = Exception.class}. Cada implementacion es un bean; {@code BaseService} las
 * recibe todas.
 */
public interface WrittenEntitiesCheck {

    /**
     * @param written lo que el flush ha insertado, modificado o borrado en logico, con la raiz
     * @throws BaseException con una alerta del catalogo, que es lo que decide el estado HTTP
     */
    void afterFlush(List<EntityChangeTracker.Change> written) throws BaseException;
}
