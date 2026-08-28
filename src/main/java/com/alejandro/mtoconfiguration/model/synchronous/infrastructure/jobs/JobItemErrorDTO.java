package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Fallo de un elemento concreto dentro de una carga masiva.
 *
 * <p>El {@code index} es la posicion en la lista que envio el cliente, y es lo unico que le permite
 * saber <b>cual</b> de sus elementos hay que corregir: los perfiles que fallan al crearse no tienen
 * todavia identificador, asi que sin la posicion el error seria indistinguible entre elementos
 * parecidos.</p>
 *
 * @param index     posicion en la lista enviada, empezando en cero
 * @param operation operacion que se intentaba ({@code create} o {@code update})
 * @param code      codigo de error cuando la excepcion lo trae; nulo si no
 * @param message   mensaje de negocio, recortado a {@code app.jobs.profile.max-item-error-message-length}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobItemErrorDTO(int index, String operation, String code, String message) {
}
