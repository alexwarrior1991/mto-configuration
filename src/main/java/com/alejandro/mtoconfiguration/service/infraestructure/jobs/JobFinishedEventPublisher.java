package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.core.messaging.AsynchronousMessage;
import com.alejandro.mtoconfiguration.core.messaging.AsynchronousMessageFactory;
import com.alejandro.mtoconfiguration.core.messaging.ConfigurationRabbitMqNames;
import com.alejandro.mtoconfiguration.core.messaging.DomainEvent;
import com.alejandro.mtoconfiguration.core.outbox.OutboxService;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Escribe en el outbox el evento {@code job.finished} de un trabajo que acaba de cerrarse.
 *
 * <p>Es lo unico que un servicio de fuera puede saber de un trabajo sin sondear su estado: quien lo
 * lanzo, como termino y cuanto hizo. Lo consume {@code mto-notification} para avisar a quien lo
 * lanzo, con un resumen en vez de con los miles de eventos de datos maestros que una importacion
 * emite por el camino.</p>
 *
 * <p>Se llama desde {@link AsyncJobStore#markFinished} y en su misma transaccion: la fila del
 * outbox se confirma con el estado terminal o no se confirma ninguno de los dos. Es la misma
 * garantia que dan los eventos de datos maestros, y por la misma razon existe el outbox.</p>
 *
 * <p>Viaja como {@link DomainEvent} y no como {@code MasterDataChangedEvent}: terminar no es un
 * alta, una modificacion ni un borrado, y el enumerado de aquel es cerrado en los consumidores.
 * Sale por el exchange propio de este servicio, no por el de datos maestros, para que quien
 * escucha {@code mto.master-data.#} no reciba nada que no sea un dato maestro.</p>
 *
 * <p>Condicional a {@code app.rabbitmq.enabled} como el listener de datos maestros: con el broker
 * apagado no se escribe nada en el outbox, y {@code AsyncJobStore} lo recibe como
 * {@code ObjectProvider} para seguir arrancando sin el.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.rabbitmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobFinishedEventPublisher {

    static final String ENTITY_NAME = "job";
    static final String EVENT_NAME = "finished";

    private final AsynchronousMessageFactory messageFactory;
    private final OutboxService outboxService;

    public void publish(AsyncJob job) {
        String jobId = job.getId().toString();
        String eventType = ConfigurationRabbitMqNames.eventType(ENTITY_NAME, EVENT_NAME);

        DomainEvent event = new DomainEvent(ENTITY_NAME, jobId, EVENT_NAME, values(job));

        AsynchronousMessage<DomainEvent> message = messageFactory.create(
                ENTITY_NAME + "-" + jobId,
                eventType,
                event
        );

        outboxService.save(
                ENTITY_NAME,
                jobId,
                eventType,
                ConfigurationRabbitMqNames.CONFIGURATION_EXCHANGE,
                ConfigurationRabbitMqNames.routingKey(ENTITY_NAME, EVENT_NAME),
                message
        );

        log.debug("Evento de fin de trabajo en el outbox jobId={} type={} status={}",
                jobId, job.getType(), job.getStatus());
    }

    /**
     * Lo que hace falta para contar como termino el trabajo. Sin el detalle de errores por elemento,
     * que puede pesar megas: para eso esta el propio trabajo, al que el aviso enlaza.
     * <p>
     * Publico porque es la definicion del payload, y {@code MessagingContractExamplesTest} la usa
     * para comprobar que el ejemplo versionado en {@code docs/messaging/examples} sigue siendo lo
     * que se publica.
     */
    public static Map<String, Object> values(AsyncJob job) {
        Map<String, Object> values = new LinkedHashMap<>();

        values.put("jobId", job.getId());
        values.put("type", job.getType());
        values.put("status", job.getStatus());
        values.put("createdBy", job.getCreatedBy());
        values.put("createdAt", job.getCreatedAt());
        values.put("startedAt", job.getStartedAt());
        values.put("finishedAt", job.getFinishedAt());
        values.put("totalItems", job.getTotalItems());
        values.put("processedItems", job.getProcessedItems());
        values.put("successfulItems", job.getSuccessfulItems());
        values.put("failedItems", job.getFailedItems());
        values.put("fileName", job.getFileName());
        values.put("trackId", job.getTrackId());
        values.put("mapperType", job.getMapperType());
        values.put("errorMessage", job.getErrorMessage());

        return values;
    }
}
