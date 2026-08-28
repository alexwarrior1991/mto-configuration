package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.repository.jpa.jobs.AsyncJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Frontera transaccional de la purga: una transaccion CORTA por lote.
 *
 * <p>Que cada lote sea su propia transaccion es el punto: el trabajo ya hecho queda confirmado
 * aunque la pasada se corte a la mitad, y ninguna transaccion se queda abierta el tiempo suficiente
 * para bloquear el vacuum de la tabla. Mismo criterio que la purga del outbox.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncJobPurgeService {

    private final AsyncJobRepository repository;
    private final ProfileJobFiles files;

    /**
     * Borra un lote de trabajos terminados y sus ficheros.
     *
     * <p>Primero el fichero y luego la fila, y ese orden importa: al reves, un corte entre las dos
     * operaciones dejaria un CSV que ya no referencia nadie y que ninguna pasada volveria a mirar.
     * Asi el peor caso es un fichero borrado cuya fila sigue ahi, que la siguiente pasada recoge
     * sin inmutarse porque el borrado del fichero es idempotente.</p>
     */
    @Transactional
    public int purgeBatch(Instant threshold, int batchSize) {
        List<AsyncJobRepository.PurgeCandidate> candidates =
                repository.findByStatusNotInAndCreatedAtBeforeOrderByCreatedAt(
                        AsyncJobRepository.ACTIVE_STATUSES, threshold, Limit.of(batchSize));

        if (candidates.isEmpty()) {
            return 0;
        }

        candidates.stream()
                .map(AsyncJobRepository.PurgeCandidate::getFileName)
                .filter(fileName -> fileName != null && !fileName.isBlank())
                .forEach(files::delete);

        List<UUID> ids = candidates.stream().map(AsyncJobRepository.PurgeCandidate::getId).toList();
        repository.deleteAllByIdInBatch(ids);

        return candidates.size();
    }
}
