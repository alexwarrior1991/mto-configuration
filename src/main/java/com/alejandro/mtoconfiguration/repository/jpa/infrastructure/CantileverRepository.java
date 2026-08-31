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



}
