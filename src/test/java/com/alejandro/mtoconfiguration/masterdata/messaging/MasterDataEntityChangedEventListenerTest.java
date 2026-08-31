package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeApplicationEvent;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * El listener es la puerta de entrada de todo evento de datos maestros: decide QUE se
 * publica (filtro por {@code @PublishMasterDataEvent}), CON QUE FORMA (entidad tal
 * cual o resuelta via {@code MessagingEntityGraphRepository}) y bajo QUE operacion.
 * Un fallo aqui no lo detecta ningun test de {@code MasterDataEventPublisher} ni de
 * outbox, porque ambos asumen que ya les llega la entidad correcta.
 */
@ExtendWith(MockitoExtension.class)
class MasterDataEntityChangedEventListenerTest {

    @Mock
    private MasterDataEventPublisher masterDataEventPublisher;

    @Mock
    private MasterDataRepositoryResolver repositoryResolver;

    @InjectMocks
    private MasterDataEntityChangedEventListener listener;

    @PublishMasterDataEvent(name = "publicable-de-prueba")
    private static class EntidadPublicable extends BaseEntity {
        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }
    }

    private static class EntidadNoPublicable extends BaseEntity {
        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }
    }

    private EntidadPublicable entidadPublicable(Long id) {
        EntidadPublicable entity = new EntidadPublicable();
        entity.setId(id);
        return entity;
    }

    @Test
    void unaEntidadNulaNoPublicaNada() {
        listener.onEntityChanged(new EntityChangeApplicationEvent<>(null, EntityChangeOperation.CREATED));

        verifyNoInteractions(masterDataEventPublisher, repositoryResolver);
    }

    @Test
    void unaEntidadSinIdTodaviaNoEsPublicable() {
        // Antes de que el id exista (p.ej. a mitad de construir la entidad) no hay nada
        // que correlacionar en el mensaje.
        listener.onEntityChanged(new EntityChangeApplicationEvent<>(entidadPublicable(null), EntityChangeOperation.CREATED));

        verifyNoInteractions(masterDataEventPublisher, repositoryResolver);
    }

    @Test
    void unaOperacionNulaNoPublicaNada() {
        listener.onEntityChanged(new EntityChangeApplicationEvent<>(entidadPublicable(1L), null));

        verifyNoInteractions(masterDataEventPublisher, repositoryResolver);
    }

    @Test
    void unaEntidadSinLaAnotacionPublishMasterDataEventNoPublicaNada() {
        EntidadNoPublicable entity = new EntidadNoPublicable();
        entity.setId(1L);

        listener.onEntityChanged(new EntityChangeApplicationEvent<>(entity, EntityChangeOperation.CREATED));

        verifyNoInteractions(masterDataEventPublisher, repositoryResolver);
    }

    @Test
    void siNoHayRepositorioResueltoSePublicaLaEntidadTalCualLlegoEnElEvento() {
        EntidadPublicable entity = entidadPublicable(1L);
        when(repositoryResolver.resolve(entity)).thenReturn(Optional.empty());

        listener.onEntityChanged(new EntityChangeApplicationEvent<>(entity, EntityChangeOperation.CREATED));

        verify(masterDataEventPublisher).publish(entity, MasterDataOperation.CREATED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unRepositorioQueNoEsDeMensajeriaSePublicaLaEntidadTalCualLlegoEnElEvento() {
        EntidadPublicable entity = entidadPublicable(1L);
        JpaRepository<IEntity, Long> repository = mock(JpaRepository.class);
        when(repositoryResolver.resolve(entity)).thenReturn(Optional.of(repository));

        listener.onEntityChanged(new EntityChangeApplicationEvent<>(entity, EntityChangeOperation.CREATED));

        verify(masterDataEventPublisher).publish(entity, MasterDataOperation.CREATED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unRepositorioDeMensajeriaResuelveElGrafoCompletoYEseEsElQueSePublica() {
        EntidadPublicable entityDelEvento = entidadPublicable(1L);
        EntidadPublicable entityResueltaConElGrafoCompleto = entidadPublicable(1L);

        JpaRepository<IEntity, Long> repository = (JpaRepository<IEntity, Long>) mock(
                JpaRepository.class,
                withSettings().extraInterfaces(MessagingEntityGraphRepository.class));
        MessagingEntityGraphRepository<IEntity> messagingRepository = (MessagingEntityGraphRepository<IEntity>) repository;
        when(messagingRepository.findByIdForMessaging(1L)).thenReturn(Optional.of(entityResueltaConElGrafoCompleto));
        when(repositoryResolver.resolve(entityDelEvento)).thenReturn(Optional.of(repository));

        listener.onEntityChanged(new EntityChangeApplicationEvent<>(entityDelEvento, EntityChangeOperation.CREATED));

        verify(masterDataEventPublisher).publish(entityResueltaConElGrafoCompleto, MasterDataOperation.CREATED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void siElRepositorioDeMensajeriaNoEncuentraLaEntidadSePublicaLaOriginalDelEvento() {
        EntidadPublicable entity = entidadPublicable(1L);

        JpaRepository<IEntity, Long> repository = (JpaRepository<IEntity, Long>) mock(
                JpaRepository.class,
                withSettings().extraInterfaces(MessagingEntityGraphRepository.class));
        MessagingEntityGraphRepository<IEntity> messagingRepository = (MessagingEntityGraphRepository<IEntity>) repository;
        when(messagingRepository.findByIdForMessaging(anyLong())).thenReturn(Optional.empty());
        when(repositoryResolver.resolve(entity)).thenReturn(Optional.of(repository));

        listener.onEntityChanged(new EntityChangeApplicationEvent<>(entity, EntityChangeOperation.CREATED));

        verify(masterDataEventPublisher).publish(entity, MasterDataOperation.CREATED);
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED, CREATED",
            "UPDATED, UPDATED",
            "DELETED, DELETED"
    })
    void laOperacionDeCambioDeEntidadSeTraduceALaOperacionDeDatosMaestrosEquivalente(
            EntityChangeOperation operacionOrigen,
            MasterDataOperation operacionEsperada
    ) {
        EntidadPublicable entity = entidadPublicable(1L);
        when(repositoryResolver.resolve(entity)).thenReturn(Optional.empty());

        listener.onEntityChanged(new EntityChangeApplicationEvent<>(entity, operacionOrigen));

        verify(masterDataEventPublisher).publish(entity, operacionEsperada);
    }
}
