package com.alejandro.mtoconfiguration.core.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "outbox_message")
public class OutboxMessage {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String aggregateType;

    @Column(nullable = false, length = 150)
    private String aggregateId;

    @Column(nullable = false, length = 150)
    private String eventType;

    @Column(nullable = false, length = 200)
    private String exchangeName;

    @Column(nullable = false, length = 200)
    private String routingKey;

    /**
     * {@code @Lob} sobre un String hace que PostgreSQL use una columna {@code oid},
     * es decir, un large object en {@code pg_largeobject} referenciado por OID. Eso
     * trae dos problemas serios para el outbox: el contenido solo es legible con la
     * transaccion abierta, y al borrar la fila el large object NO se borra, queda
     * huerfano hasta que alguien pase un vacuumlo. Con purga automatica de mensajes
     * publicados, la tabla adelgazaria mientras la base de datos sigue engordando.
     * <p>
     * LONGVARCHAR lo mapea a {@code text}, que es lo que se quiere: un JSON.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutboxStatus status;

    @Column(nullable = false)
    private Integer attempts;

    @Column(nullable = false)
    private Integer maxAttempts;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant nextAttemptAt;

    private Instant publishedAt;

    @Column(length = 1000)
    private String lastError;
}
