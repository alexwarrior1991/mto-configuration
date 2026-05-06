package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CantileverRepository extends CRUDRepository<Cantilever> {

    // Optimización: Búsqueda directa por Profile (relación frecuente)
    List<Cantilever> findByProfileId(Long profileId);

    // Optimización: Keyset Pagination para scroll infinito o procesos masivos
    @Query("SELECT c FROM Cantilever c WHERE c.profile.id = :profileId AND c.id > :lastId ORDER BY c.id ASC")
    List<Cantilever> findNextPageByProfile(@Param("profileId") Long profileId, @Param("lastId") Long lastId, Pageable pageable);
}
