package com.alejandro.mtoconfiguration.entity.commons;

import jakarta.persistence.EntityNotFoundException;
import org.hibernate.ObjectNotFoundException;

/**
 * Una referencia a uno hacia otra fila con borrado logico, que puede estar borrada.
 *
 * <p>El {@code DELETE} directo de un maestro no toca lo que cuelga de el (README_API.md §4): borrar
 * una via deja sus perfiles, sus aisladores y sus puntos de acceso apuntando a ella; borrar una
 * estacion, sus seccionadores; borrar un poste, los suyos. La clave ajena sigue ahi, pero el
 * {@code @SQLRestriction("deleted = false")} de la clase esconde la fila: el proxy de la relacion no
 * se puede inicializar, y cualquier {@code getName()} sobre el lanza
 * {@code EntityNotFoundException}. Un mapper que nombraba a la via de un perfil hacia que ese perfil
 * no se pudiese ni leer ni borrar.
 *
 * <p>Quien tiene que <b>leer</b> a traves de una relacion asi pregunta aqui: una referencia borrada
 * (o que no existe) es {@code null}, igual que la que no esta. Quien <b>escribe</b> usa
 * {@code BaseMapper.requireNotGone}, que la rechaza con su campo.
 */
public final class LiveReferences {

    private LiveReferences() {
    }

    /** La referencia, o {@code null} si no la hay, esta borrada o su fila no existe. */
    public static <T extends CRUDEntity> T orNull(T reference) {
        return isLive(reference) ? reference : null;
    }

    /**
     * Si hay referencia y su fila se puede cargar: existe y no estaba borrada. Inicializa el proxy.
     *
     * <p>No mira {@code isDeleted()}: una fila borrada no se carga nunca (la esconde la restriccion
     * de la clase), asi que una entidad cargada que dice estar borrada la ha borrado <b>esta misma
     * peticion</b> (la via que un {@code PUT} de su paquete acaba de quitar, con sus perfiles), y lo
     * que la nombra en ese mismo cuerpo se sigue tratando como antes.
     */
    public static boolean isLive(CRUDEntity reference) {
        if (reference == null) {
            return false;
        }
        try {
            reference.isDeleted(); // inicializa el proxy; si la fila no esta, lanza
            return true;
        } catch (EntityNotFoundException | ObjectNotFoundException missing) {
            return false;
        }
    }
}
