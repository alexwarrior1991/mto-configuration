package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.infrastructure.TrackAccessPoint;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackAccessPointRepository;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeApplicationEvent;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Los puntos de acceso se vuelven a publicar cuando cambia su vía o su estación, porque su evento copia
 * sus nombres y el paquete de la vía, y {@code mto-resources} no escucha esos eventos.
 */
@ExtendWith(MockitoExtension.class)
class TrackAccessPointRepublisherTest {

    @Mock
    private TrackAccessPointRepository trackAccessPointRepository;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @InjectMocks
    private TrackAccessPointRepublisher republisher;

    private static TrackAccessPoint accessPoint(long id) {
        TrackAccessPoint accessPoint = new TrackAccessPoint();
        accessPoint.setId(id);
        return accessPoint;
    }

    @SuppressWarnings("unchecked")
    private List<EntityChangeApplicationEvent<?>> published(int times) {
        ArgumentCaptor<EntityChangeApplicationEvent<?>> captor = ArgumentCaptor.forClass(EntityChangeApplicationEvent.class);
        verify(applicationEventPublisher, times(times)).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("modificar una vía publica un UPDATED por cada uno de sus accesos")
    void unaViaModificada() {
        Track track = new Track();
        track.setId(3L);
        when(trackAccessPointRepository.findByTrackIdOrderByKpAscIdAsc(3L))
                .thenReturn(List.of(accessPoint(70L), accessPoint(71L)));

        republisher.onEntityChanged(new EntityChangeApplicationEvent<>(track, EntityChangeOperation.UPDATED));

        assertThat(published(2))
                .extracting(event -> event.entity().getId(), EntityChangeApplicationEvent::operation)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(70L, EntityChangeOperation.UPDATED),
                        org.assertj.core.groups.Tuple.tuple(71L, EntityChangeOperation.UPDATED));
    }

    @Test
    @DisplayName("modificar una estación publica los suyos")
    void unaEstacionModificada() {
        Station station = new Station();
        station.setId(12L);
        when(trackAccessPointRepository.findByStationIdOrderByKpAscIdAsc(12L)).thenReturn(List.of(accessPoint(70L)));

        republisher.onEntityChanged(new EntityChangeApplicationEvent<>(station, EntityChangeOperation.UPDATED));

        assertThat(published(1)).singleElement()
                .satisfies(event -> assertThat(event.entity()).isInstanceOf(TrackAccessPoint.class));
    }

    @Test
    @DisplayName("un alta, un borrado u otra entidad no publican nada")
    void loDemasNo() {
        Track track = new Track();
        track.setId(3L);
        Profile profile = new Profile();
        profile.setId(7L);

        republisher.onEntityChanged(new EntityChangeApplicationEvent<>(track, EntityChangeOperation.CREATED));
        republisher.onEntityChanged(new EntityChangeApplicationEvent<>(track, EntityChangeOperation.DELETED));
        republisher.onEntityChanged(new EntityChangeApplicationEvent<>(profile, EntityChangeOperation.UPDATED));
        republisher.onEntityChanged(new EntityChangeApplicationEvent<>(accessPoint(70L), EntityChangeOperation.UPDATED));

        verifyNoInteractions(trackAccessPointRepository, applicationEventPublisher);
    }
}
