package com.alejandro.mtoconfiguration.core.messaging;

import java.util.Map;

/**
 * El {@code data} de los eventos que no son de datos maestros.
 * <p>
 * Los datos maestros viajan como {@code MasterDataChangedEvent}, cuya {@code operation} es un
 * enumerado cerrado que los consumidores deserializan tal cual: un valor nuevo los rompe. Lo demas
 * que este servicio cuenta —hoy el final de un trabajo— no es un alta, una modificacion o un
 * borrado, asi que lleva su nombre de evento en texto y el consumidor decide que hacer con lo que
 * no conoce. Es la misma forma que usaran los productores de los otros servicios del dominio.
 *
 * @param entityName lo que cambio, en minusculas y con guiones ({@code job})
 * @param entityId   su identificador, como texto
 * @param eventName  que paso, en minusculas y con guiones ({@code finished})
 * @param values     lo que hace falta para contarlo, sin secretos
 */
public record DomainEvent(
        String entityName,
        String entityId,
        String eventName,
        Map<String, Object> values
) {
}
