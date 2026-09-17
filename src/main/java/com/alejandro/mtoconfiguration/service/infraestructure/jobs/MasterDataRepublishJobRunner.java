package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.enums.jobs.MasterDataRepublishTarget;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

/**
 * Recorre los datos maestros seleccionados y republica cada uno como {@code UPDATED}.
 *
 * <h2>Que resuelve</h2>
 *
 * <p>Los eventos de datos maestros solo nacen al pasar por la capa de servicio
 * ({@code BaseService.create/update/bulkCreate} y {@code CRUDService.delete}). Lo que ya estaba en
 * la base cuando se conecto un consumidor nuevo no publico nunca nada, asi que ese consumidor nace
 * vacio. Esto lo rellena escribiendo, por cada entidad que ya existe, el mismo evento que habria
 * escrito una edicion real.</p>
 *
 * <h2>Como recorre</h2>
 *
 * <p>Paginas de <b>identificadores</b> por clave ({@code findIdsForRepublish}), y cada id releido
 * con {@code findByIdForMessaging} —el metodo con {@code @EntityGraph} que usa el listener de un
 * cambio real—. Asi el payload republicado es identico al de una edicion y el mapper no dispara un
 * select por relacion.</p>
 *
 * <p>El keyset avanza con el ultimo id de la pagina y termina cuando una pagina vuelve vacia; la
 * memoria no depende del tamaño de la seleccion. Una entidad creada durante el recorrido con un id
 * mayor que el ultimo visto entra en el republicado, y una con un id menor no: da igual, porque su
 * alta publico su propio evento.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MasterDataRepublishJobRunner {

    private final ProfileRepository profileRepository;
    private final DisconnectorRepository disconnectorRepository;
    private final SectionInsulatorRepository sectionInsulatorRepository;
    private final MasterDataRepublishBatchPublisher batchPublisher;
    private final AsyncJobProperties properties;

    void run(UUID jobId,
             MasterDataRepublishTarget target,
             Long trackId,
             Long stationId,
             ProfileJobProgress progress) {

        int batchSize = Math.max(1, properties.getRepublish().getBatchSize());
        int index = 0;

        for (MasterDataRepublishTarget each : target.expand()) {
            index = republish(jobId, each, trackId, stationId, batchSize, index, progress);
        }
    }

    /** Recorre un tipo entero y devuelve el indice por el que continua el siguiente. */
    private int republish(UUID jobId,
                          MasterDataRepublishTarget target,
                          Long trackId,
                          Long stationId,
                          int batchSize,
                          int indexBase,
                          ProfileJobProgress progress) {

        Long filter = switch (target) {
            case PROFILE -> trackId;
            case DISCONNECTOR, SECTION_INSULATOR -> stationId;
            case ALL -> throw new IllegalStateException("ALL no se recorre: se expande antes");
        };

        BiFunction<Long, Pageable, List<Long>> idPage = idPageOf(target, filter);
        LongFunction<Optional<? extends IEntity>> reader = readerOf(target);
        String entityName = target.getParameter();

        Pageable page = PageRequest.ofSize(batchSize);
        long lastId = 0L;
        int index = indexBase;

        while (true) {
            List<Long> ids = idPage.apply(lastId, page);

            if (ids.isEmpty()) {
                return index;
            }

            publishBatch(jobId, ids, entityName, reader, index, progress);

            index += ids.size();
            lastId = ids.getLast();
        }
    }

    /**
     * Un lote, con su fallo acotado al lote.
     *
     * <p>El lote es una transaccion, asi que no hay exito parcial que contar: si no se confirma, no
     * se publico ninguno de sus eventos. Por eso un fallo cuenta los {@code ids} enteros como
     * fallidos en vez de fingir que los que iban antes del malo salieron.</p>
     */
    private void publishBatch(UUID jobId,
                              List<Long> ids,
                              String entityName,
                              LongFunction<Optional<? extends IEntity>> reader,
                              int indexBase,
                              ProfileJobProgress progress) {
        try {
            MasterDataRepublishBatchPublisher.BatchResult result =
                    batchPublisher.publishBatch(ids, entityName, reader, indexBase);

            for (int i = 0; i < result.succeeded(); i++) {
                progress.itemSucceeded();
            }

            result.failures().forEach(failure ->
                    progress.itemFailed(failure.index(), entityName, failure.code(), failure.message()));
        } catch (RuntimeException e) {
            log.warn("Fallo el lote de republicado jobId={} entity={} desde el indice {}",
                    jobId, entityName, indexBase, e);

            String code = e.getClass().getSimpleName();
            String message = messageOf(e);

            for (int i = 0; i < ids.size(); i++) {
                progress.itemFailed(indexBase + i, entityName, code, message);
            }
        }
    }

    private BiFunction<Long, Pageable, List<Long>> idPageOf(MasterDataRepublishTarget target, Long filter) {
        return switch (target) {
            case PROFILE -> (lastId, page) -> profileRepository.findIdsForRepublish(filter, lastId, page);
            case DISCONNECTOR -> (lastId, page) -> disconnectorRepository.findIdsForRepublish(filter, lastId, page);
            case SECTION_INSULATOR ->
                    (lastId, page) -> sectionInsulatorRepository.findIdsForRepublish(filter, lastId, page);
            // Inalcanzable: run() expande ALL en los tres tipos concretos. Se lanza en vez de
            // devolver una pagina vacia porque un republicado que no republica nada y termina
            // COMPLETED es el peor resultado posible: nadie se entera.
            case ALL -> throw new IllegalStateException("ALL no se recorre: se expande antes");
        };
    }

    private LongFunction<Optional<? extends IEntity>> readerOf(MasterDataRepublishTarget target) {
        return switch (target) {
            case PROFILE -> profileRepository::findByIdForMessaging;
            case DISCONNECTOR -> disconnectorRepository::findByIdForMessaging;
            case SECTION_INSULATOR -> sectionInsulatorRepository::findByIdForMessaging;
            case ALL -> throw new IllegalStateException("ALL no se recorre: se expande antes");
        };
    }

    private String messageOf(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
