package com.alejandro.mtoconfiguration.entity.commons;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Setter;
import org.hibernate.annotations.*;

import org.hibernate.envers.Audited;

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
 * manda, y {@code orphanRemoval} los borra <b>fisicamente</b>. El borrado logico acabaria siendo
 * fisico en la siguiente modificacion del padre.
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

    public void restore() {
        this.deleted = false;
    }
}
