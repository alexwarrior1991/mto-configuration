package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.core.messaging.AsynchronousMessage;
import com.alejandro.mtoconfiguration.core.messaging.AsynchronousMessageFactory;
import com.alejandro.mtoconfiguration.core.outbox.OutboxService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Este es el punto donde una entidad de negocio se convierte en el mensaje que
 * termina en el outbox: si el {@code referenceId}, el {@code eventType} o el
 * {@code routingKey} se desalinean aqui, el fallo no lo detecta ningun test de
 * outbox ni de topologia, porque ambos asumen que quien les pasa los datos ya
 * acerto.
 */
@ExtendWith(MockitoExtension.class)
class MasterDataEventPublisherTest {

    @Mock
    private AsynchronousMessageFactory asynchronousMessageFactory;

    @Mock
    private OutboxService outboxService;

    @Mock
    private MasterDataEntityNameResolver entityNameResolver;

    @Mock
    private MasterDataEntityIdResolver entityIdResolver;

    @Mock
    private MasterDataEventPayloadExtractor payloadExtractor;

    @InjectMocks
    private MasterDataEventPublisher publisher;

    private AsynchronousMessage<MasterDataChangedEvent> mensajeDevueltoPorLaFactoria() {
        return new AsynchronousMessage<>(
                UUID.randomUUID(),
                "station-10",
                "mto-configuration",
                Instant.now(),
                "MASTER_DATA_STATION_CREATED",
                new MasterDataChangedEvent("station", "10", MasterDataOperation.CREATED, Map.of("id", 10L)),
                "hash"
        );
    }

    @Test
    void resuelveNombreIdYPayloadDeLaEntidadAntesDePublicar() {
        Object entity = new Object();
        Map<String, Object> values = Map.of("id", 10L);

        when(entityNameResolver.resolve(entity)).thenReturn("station");
        when(entityIdResolver.resolve(entity)).thenReturn("10");
        when(payloadExtractor.extract(entity)).thenReturn(values);
        when(asynchronousMessageFactory.create(anyString(), anyString(), any(MasterDataChangedEvent.class)))
                .thenReturn(mensajeDevueltoPorLaFactoria());

        publisher.publish(entity, MasterDataOperation.CREATED);

        verify(entityNameResolver).resolve(entity);
        verify(entityIdResolver).resolve(entity);
        verify(payloadExtractor).extract(entity);
    }

    @Test
    void elReferenceIdJuntaElNombreDeLaEntidadYSuIdConUnGuion() {
        Object entity = new Object();

        when(entityNameResolver.resolve(entity)).thenReturn("station");
        when(entityIdResolver.resolve(entity)).thenReturn("10");
        when(payloadExtractor.extract(entity)).thenReturn(Map.of());
        when(asynchronousMessageFactory.create(anyString(), anyString(), any(MasterDataChangedEvent.class)))
                .thenReturn(mensajeDevueltoPorLaFactoria());

        publisher.publish(entity, MasterDataOperation.CREATED);

        ArgumentCaptor<String> referenceIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(asynchronousMessageFactory).create(referenceIdCaptor.capture(), anyString(), any());

        assertThat(referenceIdCaptor.getValue()).isEqualTo("station-10");
    }

    @Test
    void elEventTypeSigueElFormatoMasterDataEntidadOperacion() {
        Object entity = new Object();

        when(entityNameResolver.resolve(entity)).thenReturn("station");
        when(entityIdResolver.resolve(entity)).thenReturn("10");
        when(payloadExtractor.extract(entity)).thenReturn(Map.of());
        when(asynchronousMessageFactory.create(anyString(), anyString(), any(MasterDataChangedEvent.class)))
                .thenReturn(mensajeDevueltoPorLaFactoria());

        publisher.publish(entity, MasterDataOperation.UPDATED);

        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(asynchronousMessageFactory).create(anyString(), eventTypeCaptor.capture(), any());

        assertThat(eventTypeCaptor.getValue()).isEqualTo("MASTER_DATA_STATION_UPDATED");
    }

    @Test
    void elEventoQueViajaEnElMensajeLlevaElNombreElIdLaOperacionYLosValoresResueltos() {
        Object entity = new Object();
        Map<String, Object> values = Map.of("code", "S-01");

        when(entityNameResolver.resolve(entity)).thenReturn("station");
        when(entityIdResolver.resolve(entity)).thenReturn("10");
        when(payloadExtractor.extract(entity)).thenReturn(values);
        when(asynchronousMessageFactory.create(anyString(), anyString(), any(MasterDataChangedEvent.class)))
                .thenReturn(mensajeDevueltoPorLaFactoria());

        publisher.publish(entity, MasterDataOperation.DELETED);

        ArgumentCaptor<MasterDataChangedEvent> eventCaptor = ArgumentCaptor.forClass(MasterDataChangedEvent.class);
        verify(asynchronousMessageFactory).create(anyString(), anyString(), eventCaptor.capture());

        assertThat(eventCaptor.getValue())
                .isEqualTo(new MasterDataChangedEvent("station", "10", MasterDataOperation.DELETED, values));
    }

    @Test
    void elMensajeSeGuardaEnElOutboxConElExchangeYElRoutingKeyDeDatosMaestros() {
        Object entity = new Object();
        AsynchronousMessage<MasterDataChangedEvent> mensaje = mensajeDevueltoPorLaFactoria();

        when(entityNameResolver.resolve(entity)).thenReturn("station");
        when(entityIdResolver.resolve(entity)).thenReturn("10");
        when(payloadExtractor.extract(entity)).thenReturn(Map.of());
        when(asynchronousMessageFactory.create(anyString(), anyString(), any(MasterDataChangedEvent.class))).thenReturn(mensaje);

        publisher.publish(entity, MasterDataOperation.CREATED);

        verify(outboxService).save(
                eq("station"),
                eq("10"),
                eq("MASTER_DATA_STATION_CREATED"),
                eq(MasterDataRabbitMqNames.MASTER_DATA_EXCHANGE),
                eq("mto.master-data.station.created"),
                eq(mensaje)
        );
    }

    @Test
    void laSobrecargaConNombreIdYValoresYaResueltosNoVuelveAResolverNada() {
        AsynchronousMessage<MasterDataChangedEvent> mensaje = mensajeDevueltoPorLaFactoria();
        when(asynchronousMessageFactory.create(anyString(), anyString(), any(MasterDataChangedEvent.class))).thenReturn(mensaje);

        publisher.publish("station", "10", MasterDataOperation.CREATED, Map.of("id", 10L));

        verifyNoInteractions(entityNameResolver, entityIdResolver, payloadExtractor);
        verify(outboxService).save(
                eq("station"),
                eq("10"),
                eq("MASTER_DATA_STATION_CREATED"),
                eq(MasterDataRabbitMqNames.MASTER_DATA_EXCHANGE),
                eq("mto.master-data.station.created"),
                eq(mensaje)
        );
    }

    @Test
    void unFalloResolviendoElPayloadImpideQueLlegueNadaAlOutbox() {
        Object entity = new Object();

        when(entityNameResolver.resolve(entity)).thenReturn("station");
        when(entityIdResolver.resolve(entity)).thenReturn("10");
        when(payloadExtractor.extract(entity)).thenThrow(new IllegalStateException("sin mapper"));

        try {
            publisher.publish(entity, MasterDataOperation.CREATED);
        } catch (IllegalStateException expected) {
            // el propio extractor ya tiene su test dedicado; aqui solo interesa el efecto
        }

        verifyNoInteractions(outboxService);
        verify(asynchronousMessageFactory, never()).create(anyString(), anyString(), any());
    }
}
