package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CantileverRepository extends CRUDRepository<Cantilever>,
        MessagingEntityGraphRepository<Cantilever> {

    // Optimización: Búsqueda directa por Profile (relación frecuente)
    List<Cantilever> findByProfileId(Long profileId);

    /**
     * Identificadores de las mensulas de un perfil, y el de su brazo, en el orden estable
     * que fija {@code @OrderBy("id ASC")} en la entidad.
     *
     * <p>Existe para el importador del maestro, que reconcilia las mensulas <b>por posicion</b>
     * y de cada una solo necesita el id. Antes se traia el perfil entero con
     * {@code ProfileService.getById}, que ademas de mapear un DTO completo con sus diez listas
     * de valores por cada uno de los 11.714 perfiles, devuelve un <b>proxy</b>
     * ({@code getReferenceById}) que fuera de una transaccion no se puede inicializar.
     *
     * <p>Al ser una proyeccion de escalares no hay proxy que inicializar ni sesion que haga
     * falta: el resultado ya viene materializado.
     */
    @Query("""
            select c.id as cantileverId, sa.id as steadyArmId
            from Cantilever c left join c.steadyArm sa
            where c.profile.id = :profileId
            order by c.id asc
            """)
    List<CantileverIds> findIdsByProfileIdOrderByIdAsc(@Param("profileId") Long profileId);

    /** Lo unico que el importador necesita de una mensula que ya existe. */
    interface CantileverIds {
        Long getCantileverId();

        Long getSteadyArmId();
    }

    // Optimización: Keyset Pagination para scroll infinito o procesos masivos
    @Query("SELECT c FROM Cantilever c WHERE c.profile.id = :profileId AND c.id > :lastId ORDER BY c.id ASC")
    List<Cantilever> findNextPageByProfile(@Param("profileId") Long profileId, @Param("lastId") Long lastId, Pageable pageable);

    /**
     * {@code steadyArm} es obligatorio: es el lado INVERSO del {@code @OneToOne}
     * ({@code mappedBy = "cantilever"}), que Hibernate no puede proxear, asi que sin
     * el se paga un select extra.
     * <p>
     * No entran {@code profile.track} ni {@code steadyArm.steadyArmType}: de ambos el
     * mapper solo lee el id, y con acceso por propiedad ({@code @Id} sobre el getter)
     * leer el id de un proxy no lo inicializa.
     * <p>
     * {@code profile.disconnector} tampoco lo pide el mapper, lo impone Hibernate:
     * Profile.disconnector es el lado INVERSO de un {@code @OneToOne} y no puede ser
     * perezoso sin bytecode enhancement, asi que al materializar el profile se carga
     * con un select secundario. Traerlo en el grafo deja la consulta en una sentencia.
     */
    @Override
    @EntityGraph(attributePaths = {
            "profile",
            "profile.disconnector",
            "cantileverType",
            "steadyArm"
    })
    @Query("select c from Cantilever c where c.id = :id")
    Optional<Cantilever> findByIdForMessaging(@Param("id") Long id);



}
