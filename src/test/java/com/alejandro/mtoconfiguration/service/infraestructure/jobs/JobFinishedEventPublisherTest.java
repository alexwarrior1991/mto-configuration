package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.core.messaging.AsynchronousMessage;
import com.alejandro.mtoconfiguration.core.messaging.AsynchronousMessageFactory;
import com.alejandro.mtoconfiguration.core.messaging.ConfigurationRabbitMqNames;
import com.alejandro.mtoconfiguration.core.messaging.DomainEvent;
import com.alejandro.mtoconfiguration.core.outbox.OutboxService;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Aqui es donde un trabajo cerrado se convierte en el mensaje que termina en el outbox. Si el
 * exchange, la clave de enrutado o el tipo se desalinean, ningun test de outbox ni de topologia lo
 * ve: los dos asumen que quien les pasa los datos ya acerto.
 */
@ExtendWith(MockitoExtension.class)
class JobFinishedEventPublisherTest {

    private static final UUID JOB_ID = UUID.fromString("00000000-0000-4000-8000-0000000000aa");

    @Mock
    private AsynchronousMessageFactory messageFactory;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private JobFinishedEventPublisher publisher;

    private AsyncJob unaImportacionTerminada() {
        AsyncJob job = new AsyncJob();
        job.setId(JOB_ID);
        job.setType(JobType.LOV_IMPORT);
        job.setStatus(JobStatus.COMPLETED_WITH_ERRORS);
        job.setCreatedBy("config.responsable");
        job.setCreatedAt(Instant.parse("2026-09-29T07:00:00Z"));
        job.setStartedAt(Instant.parse("2026-09-29T07:00:01Z"));
        job.setFinishedAt(Instant.parse("2026-09-29T07:00:09Z"));
        job.setHeartbeatAt(Instant.parse("2026-09-29T07:00:09Z"));
        job.setTotalItems(120);
        job.setProcessedItems(120);
        job.setSuccessfulItems(118);
        job.setFailedItems(2);
        job.setFileName("lov-import-" + JOB_ID + ".json");
        job.setErrorDetailsJson("[{\"index\":7,\"operation\":\"import\",\"code\":\"LOV-IMPORT\",\"message\":\"fila rechazada\"}]");
        return job;
    }

    @SuppressWarnings("unchecked")
    private AsynchronousMessage<DomainEvent> mensajeDevueltoPorLaFactoria() {
        return new AsynchronousMessage<>(
                UUID.randomUUID(), "job-" + JOB_ID, "mto-configuration", Instant.now(),
                "CONFIGURATION_JOB_FINISHED", new DomainEvent("job", JOB_ID.toString(), "finished", Map.of()),
                "hash", null, null);
    }

    @Test
    @DisplayName("el mensaje se guarda en el outbox con el exchange propio, la clave de enrutado y el tipo del contrato")
    void elMensajeVaAlOutboxConSuDestino() {
        AsynchronousMessage<DomainEvent> mensaje = mensajeDevueltoPorLaFactoria();
        when(messageFactory.create(anyString(), anyString(), any(DomainEvent.class))).thenReturn(mensaje);

        publisher.publish(unaImportacionTerminada());

        verify(messageFactory).create(
                eq("job-" + JOB_ID),
                eq("CONFIGURATION_JOB_FINISHED"),
                any(DomainEvent.class));
        verify(outboxService).save(
                eq("job"),
                eq(JOB_ID.toString()),
                eq("CONFIGURATION_JOB_FINISHED"),
                eq(ConfigurationRabbitMqNames.CONFIGURATION_EXCHANGE),
                eq("mto.configuration.job.finished"),
                eq(mensaje));
    }

    @Test
    @DisplayName("el evento lleva la entidad, su id, el nombre del evento y como termino el trabajo")
    void elEventoCuentaComoTerminoElTrabajo() {
        when(messageFactory.create(anyString(), anyString(), any(DomainEvent.class)))
                .thenReturn(mensajeDevueltoPorLaFactoria());

        publisher.publish(unaImportacionTerminada());

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(messageFactory).create(anyString(), anyString(), captor.capture());
        DomainEvent event = captor.getValue();

        assertThat(event.entityName()).isEqualTo("job");
        assertThat(event.entityId()).isEqualTo(JOB_ID.toString());
        assertThat(event.eventName()).isEqualTo("finished");
        assertThat(event.values()).contains(
                entry("jobId", JOB_ID),
                entry("type", JobType.LOV_IMPORT),
                entry("status", JobStatus.COMPLETED_WITH_ERRORS),
                entry("createdBy", "config.responsable"),
                entry("startedAt", Instant.parse("2026-09-29T07:00:01Z")),
                entry("finishedAt", Instant.parse("2026-09-29T07:00:09Z")),
                entry("totalItems", 120),
                entry("processedItems", 120),
                entry("successfulItems", 118),
                entry("failedItems", 2),
                entry("fileName", "lov-import-" + JOB_ID + ".json"));
    }

    @Test
    @DisplayName("el detalle de errores por elemento no viaja: puede pesar megas y para eso esta el trabajo, al que el aviso enlaza")
    void elDetalleDeErroresNoViaja() {
        Map<String, Object> values = JobFinishedEventPublisher.values(unaImportacionTerminada());

        assertThat(values).containsOnlyKeys(
                "jobId", "type", "status", "createdBy", "createdAt", "startedAt", "finishedAt",
                "totalItems", "processedItems", "successfulItems", "failedItems",
                "fileName", "trackId", "mapperType", "errorMessage");
    }

    @Test
    @DisplayName("lo que el trabajo no tiene viaja como null, no desaparece: el consumidor cuenta con las claves")
    void loQueFaltaViajaComoNull() {
        AsyncJob job = unaImportacionTerminada();
        job.setFileName(null);
        job.setTotalItems(null);

        Map<String, Object> values = JobFinishedEventPublisher.values(job);

        assertThat(values).containsKey("fileName").containsKey("totalItems")
                .containsKey("trackId").containsKey("mapperType").containsKey("errorMessage");
        assertThat(values.get("fileName")).isNull();
        assertThat(values.get("errorMessage")).isNull();
    }
}
