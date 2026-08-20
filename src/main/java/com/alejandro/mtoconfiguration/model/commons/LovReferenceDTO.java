package com.alejandro.mtoconfiguration.model.commons;

/**
 * Envoltorio del id de un LOV para guardarlo en caché.
 * <p>
 * No es cosmético: el serializador de Redis usa default typing sobre tipos no
 * finales, de modo que un {@code Long} desnudo se escribe como el JSON {@code 7}
 * sin marcador de tipo y al releerlo Jackson devuelve un {@code Integer} para
 * cualquier valor que quepa en 32 bits. El proxy de caché intentaría entonces
 * devolver ese {@code Integer} donde la firma declara {@code Long} y lanzaría
 * {@code ClassCastException} en el primer acierto de caché.
 * <p>
 * Envuelto en este record, Jackson escribe el {@code @class} y el tipo se
 * conserva intacto en el viaje de ida y vuelta.
 */
public record LovReferenceDTO(Long id) {
}
