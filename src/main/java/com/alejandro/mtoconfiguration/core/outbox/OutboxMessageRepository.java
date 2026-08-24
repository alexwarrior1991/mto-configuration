package com.alejandro.mtoconfiguration.core.outbox;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    /**
     * Reclama en exclusiva un lote de mensajes publicables.
     * <p>
     * {@code FOR UPDATE SKIP LOCKED} es lo que hace que el relay sea seguro con varias
     * replicas: cada instancia se lleva un lote DISJUNTO en lugar de leer todas las
     * mismas filas y publicar el mismo evento N veces. {@code SKIP LOCKED} ademas evita
     * que las replicas se bloqueen entre si; simplemente pasan a las filas libres.
     * <p>
     * Se recogen tambien los {@code IN_PROGRESS} cuya visibilidad ha expirado: son
     * mensajes que reclamo una replica que murio antes de cerrarlos, y sin esto se
     * quedarian atascados para siempre.
     * <p>
     * {@code coalesce(next_attempt_at, created_at)} es defensivo: la columna admite
     * nulos y una fila con null jamas entraria por una comparacion directa.
     */
    @Query(value = """
            select * from {h-schema}outbox_message
            where status in ('PENDING', 'IN_PROGRESS')
              and coalesce(next_attempt_at, created_at) <= :now
            order by created_at, id
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<OutboxMessage> claimBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);

    long countByStatus(OutboxStatus status);

    @Query("select min(o.createdAt) from OutboxMessage o where o.status = :status")
    Instant findOldestCreatedAt(@Param("status") OutboxStatus status);

    List<OutboxMessage> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Limit limit);

    /**
     * Borra un lote de mensajes ya publicados y suficientemente antiguos.
     * <p>
     * Por lotes y no de una sentencia: un DELETE de millones de filas mantiene una
     * transaccion larguisima, hincha el WAL y bloquea el vacuum de la tabla. El
     * subselect con LIMIT acota cada pasada.
     */
    @Modifying
    @Query(value = """
            delete from {h-schema}outbox_message
            where id in (
                select id from {h-schema}outbox_message
                where status = 'PUBLISHED'
                  and published_at < :threshold
                order by published_at
                limit :batchSize
            )
            """, nativeQuery = true)
    int deletePublishedOlderThan(@Param("threshold") Instant threshold, @Param("batchSize") int batchSize);
}
