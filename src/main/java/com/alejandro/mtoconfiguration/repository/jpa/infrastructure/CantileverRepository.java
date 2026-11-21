package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import com.querydsl.core.types.Predicate;
import org.springframework.data.domain.Page;
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

    /**
     * Las mensulas de un perfil, con su brazo en la misma sentencia.
     *
     * <p>{@code steadyArm} es el lado inverso de un {@code @OneToOne} y Hibernate lo carga siempre
     * (ver {@code Cantilever.getSteadyArm}). En una carga por id o por la coleccion de su perfil
     * viene unido; en una consulta, solo si la consulta lo pide, y si no cuesta un
     * {@code select} de {@code steady_arm} por mensula. Por eso las tres listas de abajo lo piden.
     * Lo fija {@code SteadyArmFetchIT}.
     */
    @EntityGraph(attributePaths = "steadyArm")
    List<Cantilever> findByProfileId(Long profileId);

    /** {@code /cantilevers/filter}: la pagina filtrada, con el brazo de cada mensula. */
    @Override
    @EntityGraph(attributePaths = "steadyArm")
    Page<Cantilever> findAll(Predicate predicate, Pageable pageable);

    /** {@code /cantilevers/paged}: la pagina, con el brazo de cada mensula. */
    @Override
    @EntityGraph(attributePaths = "steadyArm")
    Page<Cantilever> findAll(Pageable pageable);

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
     */
    @Override
    @EntityGraph(attributePaths = {
            "profile",
            "cantileverType",
            "steadyArm"
    })
    @Query("select c from Cantilever c where c.id = :id")
    Optional<Cantilever> findByIdForMessaging(@Param("id") Long id);

    /**
     * Todas las ménsulas de una via para su esquema ({@code TrackSchematicService}), con su tipo y
     * su brazo, en una consulta; el servicio las agrupa por {@code profile.id}, que se lee del
     * proxy sin inicializarlo (acceso por propiedad). {@code steadyArm} es el lado INVERSO del
     * {@code @OneToOne}: sin el {@code fetch} costaria un select por ménsula.
     */
    @Query("""
            select c from Cantilever c
            join c.profile p
            left join fetch c.cantileverType
            left join fetch c.steadyArm sa
            left join fetch sa.steadyArmType
            where p.track.id = :trackId
            order by p.id asc, c.id asc
            """)
    List<Cantilever> findForSchematic(@Param("trackId") Long trackId);
}
