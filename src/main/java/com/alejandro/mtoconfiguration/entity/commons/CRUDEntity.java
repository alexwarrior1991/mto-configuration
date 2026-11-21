package com.alejandro.mtoconfiguration.entity.commons;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Setter;
import org.hibernate.annotations.*;

import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Base de las entidades con borrado logico: en lugar de borrar la fila se marca
 * {@code deleted} y {@code @SQLRestriction} la excluye de las consultas.
 *
 * <p><b>Esta restriccion de clase NO filtra las colecciones.</b> Se aplica cuando se consulta la
 * entidad —{@code findAll}, {@code findById}, la busqueda por criteria—, pero cuando Hibernate
 * carga un {@code @OneToMany} lanza la consulta de la coleccion y no le añade la condicion. El
 * resultado es que una fila borrada desaparece de su listado y sigue apareciendo dentro de su
 * padre: una via borrada se cae de {@code GET /tracks} y se sigue devolviendo en
 * {@code GET /execution-packages/&#123;id&#125;}.
 *
 * <p>Por eso cada coleccion y cada relacion uno a uno del lado inverso repite
 * {@code @SQLRestriction("deleted = false")} sobre el atributo. Si se añade una asociacion nueva a
 * una entidad con borrado logico, hay que repetirla tambien; {@code SoftDeleteIT} lo comprueba.
 *
 * <p>La consecuencia menos evidente es la que obliga a no olvidarlo: la reconciliacion de hijos
 * de los mappers compara la lista que manda el cliente con la coleccion del padre, y lo que no
 * viene lo retira. Si la coleccion arrastra hijos ya borrados, el cliente no los conoce, no los
 * manda, y la reconciliacion lo trataria como recien quitado: lo borraria otra vez, con su rama, y
 * publicaria un segundo {@code DELETED} en la siguiente modificacion del padre (ver
 * {@link #deleteCascade()}).
 *
 * <p><b>Una relacion a uno hacia otra entidad con borrado logico no lleva
 * {@code @Audited(targetAuditMode = NOT_AUDITED)}.</b> Con el, Envers (desde Hibernate 7.4, que
 * empezo a respetarlo al inicializar el proxy de una revision) lee el destino de su tabla viva, por
 * id y con esta restriccion: el destino borrado no aparece, la fila de la revision no se puede
 * reconstruir y el historial la devuelve con {@code entity} a {@code null}. Sin el, lo lee de la
 * tabla {@code _aud} del destino, que conserva tambien al borrado. Las relaciones a catalogos si lo
 * llevan: su borrado es fisico y solo si nadie los usa. Lo fija
 * {@code EntityRevisionsIT} («si el poste de entonces se borro despues...»).
 */
@Setter
@DynamicUpdate
@DynamicInsert
@MappedSuperclass
@Audited
@SQLRestriction("deleted = false")
public abstract class CRUDEntity extends BaseEntity {

    @Column(name = "deleted", nullable = false)
    private boolean deleted = Boolean.FALSE;

    public boolean isDeleted() {
        return deleted;
    }

    public void delete() {
        this.deleted = Boolean.TRUE;
    }

    /**
     * Borra en logico esta entidad <b>y todo lo que cuelga de ella</b>.
     *
     * <p>Es lo que le pasa al hijo que una modificacion de su padre deja fuera de la coleccion
     * ({@code BaseMapper.mergeCollection}). Hasta ahora lo borraba {@code orphanRemoval}, en
     * fisico y con toda su rama, y sin que nadie se enterase: no habia evento {@code DELETED}, y
     * el consumidor conservaba el perfil o el seccionador para siempre. Ahora se marca, como en
     * su {@code DELETE} directo, la fila se queda para la auditoria y cada entidad de la rama
     * publica su evento.
     *
     * <p>Baja por la rama, a diferencia de {@link #delete()}, que es lo que usa el {@code DELETE}
     * directo y deja a los hijos donde estaban: aqui la peticion dice que el hijo ya no esta en
     * su padre, y antes de este cambio su rama desaparecia con el. Por eso las colecciones de
     * hijos ya no llevan {@code orphanRemoval}: sacar al hijo de la coleccion en memoria no
     * tiene que borrar la fila.
     */
    public void deleteCascade() {
        delete();
        ownedChildren().forEach(CRUDEntity::deleteCascade);
    }

    /**
     * Los hijos que no existen sin esta entidad: los de las colecciones que su mapper reconcilia.
     * No es una propiedad (no empieza por {@code get}), asi que Hibernate no la mapea.
     */
    protected Collection<? extends CRUDEntity> ownedChildren() {
        return List.of();
    }

    /**
     * Deja fuera los hijos borrados, para una entidad <b>reconstruida por Envers</b> en una revision.
     *
     * <p>En una lectura viva los quita el {@code @SQLRestriction("deleted = false")} de cada
     * coleccion. En el historial no: Envers lee los hijos de una revision de la gemela {@code _aud}
     * del hijo, donde esa restriccion no se aplica, y el borrado logico es para Envers una
     * modificacion mas. La mensula quitada de un perfil, el brazo quitado de una mensula, la aguja
     * quitada de un aislador o el seccionador borrado de un poste seguian saliendo en todas las
     * revisiones posteriores a su borrado, cuando el historial promete lo que la fila tenia entonces.
     * El hijo llega con su {@code deleted} de esa revision, asi que basta con mirarlo.
     *
     * <p>Solo lo llama {@code BaseService.findRevisions}, sobre la entidad de una revision, que no
     * esta gestionada: nunca sobre una entidad viva, donde quitar un hijo de la coleccion seria
     * escribir. La sobrescribe la entidad cuya fila en las listas lleva hijos (el perfil con sus
     * mensulas y sus seccionadores, la mensula con su brazo, el aislador con sus agujas); las demas
     * salen sin sus colecciones y no tienen nada que quitar. No es una propiedad: no empieza por
     * {@code get}.
     */
    public void forgetDeletedChildren() {
        // Sin hijos en su fila: nada que quitar.
    }

    /** Los vivos de una coleccion de hijos, en su orden. */
    protected static <C extends CRUDEntity> List<C> alive(Collection<C> children) {
        if (children == null) {
            return new ArrayList<>();
        }
        List<C> alive = new ArrayList<>();
        for (C child : children) {
            if (child != null && !child.isDeleted()) {
                alive.add(child);
            }
        }
        return alive;
    }

    public void restore() {
        this.deleted = false;
    }
}
