package com.alejandro.mtoconfiguration.core.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Frontera transaccional de la purga: una transaccion CORTA por lote.
 * <p>
 * Que cada lote sea su propia transaccion es el punto: el trabajo ya hecho queda
 * confirmado aunque la pasada se corte a la mitad, y ninguna transaccion se queda
 * abierta el tiempo suficiente para bloquear el vacuum.
 */
@Service
@RequiredArgsConstructor
public class OutboxPurgeService {

    private final OutboxMessageRepository outboxMessageRepository;

    @Transactional
    public int purgeBatch(Instant threshold, int batchSize) {
        return outboxMessageRepository.deletePublishedOlderThan(threshold, batchSize);
    }
}
