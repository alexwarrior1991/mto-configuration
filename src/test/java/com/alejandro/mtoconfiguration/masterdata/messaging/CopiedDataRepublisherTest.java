package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeApplicationEvent;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeOperation;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Que cambio de una via o de una estacion vuelve a publicar a quien: solo lo que el evento del
 * dependiente copia, por lotes, y sin repetir lo que la escritura ya publica.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CopiedDataRepublisher")
class CopiedDataRepublisherTest {

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private DisconnectorRepository disconnectorRepository;
    @Mock
    private SectionInsulatorRepository sectionInsulatorRepository;
    @Mock
    private MasterDataEventPublisher masterDataEventPublisher;
    @InjectMocks
    private CopiedDataRepublisher republisher;

    private EntityChangeTracker.Scope scope;

    @BeforeEach
    void abre() {
        scope = EntityChangeTracker.open();
    }

    @AfterEach
    void cierra() {
        scope.close();
    }

    private static Track track(long id) {
        Track track = new Track();
        track.setId(id);
        return track;
    }

    private static Profile profile(long id) {
        Profile profile = new Profile();
        profile.setId(id);
        return profile;
    }

    private static Disconnector disconnector(long id) {
        Disconnector disconnector = new Disconnector();
        disconnector.setId(id);
        return disconnector;
    }

    private static SectionInsulator insulator(long id) {
        SectionInsulator insulator = new SectionInsulator();
        insulator.setId(id);
        return insulator;
    }

    private void updated(Object entity, String... changed) {
        EntityChangeTracker.recordChangedProperties(entity, List.of(changed));
        republisher.onEntityChanged(new EntityChangeApplicationEvent<>(
                (com.alejandro.mtoconfiguration.entity.commons.IEntity) entity, EntityChangeOperation.UPDATED));
    }

    private List<Object> published(int times) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(masterDataEventPublisher, times(times)).publish(captor.capture(), any(MasterDataOperation.class));
        return captor.getAllValues();
    }

    @Test
    @DisplayName("la via pasa a otro paquete: sus perfiles primero, y detras los seccionadores y aisladores que estan en ella")
    void laViaCambiaDePaquete() {
        when(profileRepository.findIdsByTrackId(3L)).thenReturn(List.of(10L, 11L));
        when(profileRepository.findAllByIdForMessaging(List.of(10L, 11L))).thenReturn(List.of(profile(10L), profile(11L)));
        when(disconnectorRepository.findIdsLocatedOnTrack(3L)).thenReturn(List.of(20L));
        when(disconnectorRepository.findAllByIdForMessaging(List.of(20L))).thenReturn(List.of(disconnector(20L)));
        when(sectionInsulatorRepository.findIdsLocatedOnTrack(3L)).thenReturn(List.of(30L));
        when(sectionInsulatorRepository.findAllByIdForMessaging(List.of(30L))).thenReturn(List.of(insulator(30L)));

        updated(track(3L), "executionPackage", "versionNumber");

        assertThat(published(4)).extracting(Object::getClass, entity -> ((com.alejandro.mtoconfiguration.entity.commons.IEntity) entity).getId())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Profile.class, 10L),
                        org.assertj.core.groups.Tuple.tuple(Profile.class, 11L),
                        org.assertj.core.groups.Tuple.tuple(Disconnector.class, 20L),
                        org.assertj.core.groups.Tuple.tuple(SectionInsulator.class, 30L));
        verify(masterDataEventPublisher, times(4)).publish(any(Object.class), org.mockito.ArgumentMatchers.eq(MasterDataOperation.UPDATED));
        verify(disconnectorRepository, never()).findIdsNamingTrack(any());
        verify(sectionInsulatorRepository, never()).findIdsNamingTrack(any());
    }

    @Test
    @DisplayName("la via cambia de nombre: sus perfiles y lo que la nombra; con el paquete a la vez, cada uno una sola vez")
    void laViaCambiaDeNombreYDePaquete() {
        when(profileRepository.findIdsByTrackId(3L)).thenReturn(List.of());
        when(disconnectorRepository.findIdsNamingTrack(3L)).thenReturn(List.of(20L, 21L));
        when(disconnectorRepository.findIdsLocatedOnTrack(3L)).thenReturn(List.of(21L, 22L));
        when(disconnectorRepository.findAllByIdForMessaging(List.of(20L, 21L, 22L)))
                .thenReturn(List.of(disconnector(20L), disconnector(21L), disconnector(22L)));
        when(sectionInsulatorRepository.findIdsNamingTrack(3L)).thenReturn(List.of());
        when(sectionInsulatorRepository.findIdsLocatedOnTrack(3L)).thenReturn(List.of());

        updated(track(3L), "name", "executionPackage");

        assertThat(published(3)).allMatch(Disconnector.class::isInstance);
    }

    @Test
    @DisplayName("desactivar la via o cambiar sus estaciones solo vuelve a publicar sus perfiles")
    void loQueSoloCopiaElPerfil() {
        when(profileRepository.findIdsByTrackId(3L)).thenReturn(List.of(10L));
        when(profileRepository.findAllByIdForMessaging(List.of(10L))).thenReturn(List.of(profile(10L)));

        updated(track(3L), "stations");

        assertThat(published(1)).singleElement().isInstanceOf(Profile.class);
        verifyNoInteractions(disconnectorRepository);
        verify(sectionInsulatorRepository, never()).findIdsNamingTrack(any());
        verify(sectionInsulatorRepository, never()).findIdsLocatedOnTrack(any());
    }

    @Test
    @DisplayName("una via que no cambia nada de lo que se copia, o un UPDATED fuera de una escritura, no publica nada")
    void sinCambiosCopiados() {
        updated(track(3L), "versionNumber", "versionDate", "versionUser");
        republisher.onEntityChanged(new EntityChangeApplicationEvent<>(track(4L), EntityChangeOperation.UPDATED));

        EntityChangeTracker.recordChangedProperties(track(5L), List.of("name"));
        republisher.onEntityChanged(new EntityChangeApplicationEvent<>(track(5L), EntityChangeOperation.DELETED));
        republisher.onEntityChanged(new EntityChangeApplicationEvent<>(track(5L), EntityChangeOperation.CREATED));

        verifyNoInteractions(profileRepository, disconnectorRepository, sectionInsulatorRepository,
                masterDataEventPublisher);
    }

    @Test
    @DisplayName("la estacion cambia de nombre: sus seccionadores y sus aisladores; otro cambio suyo, nada")
    void laEstacionCambiaDeNombre() {
        Station station = new Station();
        station.setId(12L);
        when(disconnectorRepository.findIdsByStationId(12L)).thenReturn(List.of(20L));
        when(disconnectorRepository.findAllByIdForMessaging(List.of(20L))).thenReturn(List.of(disconnector(20L)));
        when(sectionInsulatorRepository.findIdsByStationId(12L)).thenReturn(List.of(30L));
        when(sectionInsulatorRepository.findAllByIdForMessaging(List.of(30L))).thenReturn(List.of(insulator(30L)));

        updated(station, "name");

        assertThat(published(2)).extracting(Object::getClass)
                .containsExactly(Disconnector.class, SectionInsulator.class);
        verifyNoInteractions(profileRepository);

        Station other = new Station();
        other.setId(13L);
        updated(other, "executionPackage");

        verify(disconnectorRepository, never()).findIdsByStationId(13L);
    }

    @Test
    @DisplayName("lo que la escritura ya publica por su cuenta no se carga ni sale otra vez, y lo republicado no se repite")
    void sinRepetirLoQueYaSale() {
        EntityChangeTracker.record(profile(10L), EntityChangeOperation.UPDATED);
        when(profileRepository.findIdsByTrackId(3L)).thenReturn(List.of(10L, 11L));
        when(profileRepository.findAllByIdForMessaging(List.of(11L))).thenReturn(List.of(profile(11L)));

        updated(track(3L), "enabled");
        updated(track(3L), "enabled");

        assertThat(published(1)).singleElement()
                .extracting(entity -> ((Profile) entity).getId()).isEqualTo(11L);
        verify(profileRepository, times(1)).findAllByIdForMessaging(anyCollection());
    }

    @Test
    @DisplayName("los perfiles de una via grande se cargan por lotes, no uno a uno")
    void porLotes() {
        List<Long> ids = LongStream.rangeClosed(1, CopiedDataRepublisher.BATCH_SIZE * 2L + 5).boxed().toList();
        when(profileRepository.findIdsByTrackId(3L)).thenReturn(ids);
        when(profileRepository.findAllByIdForMessaging(anyCollection())).thenAnswer(call -> {
            Collection<Long> batch = call.getArgument(0);
            return new ArrayList<>(batch.stream().map(CopiedDataRepublisherTest::profile).toList());
        });

        updated(track(3L), "enabled");

        ArgumentCaptor<Collection<Long>> batches = ArgumentCaptor.forClass(Collection.class);
        verify(profileRepository, times(3)).findAllByIdForMessaging(batches.capture());
        assertThat(batches.getAllValues()).extracting(Collection::size)
                .containsExactly(CopiedDataRepublisher.BATCH_SIZE, CopiedDataRepublisher.BATCH_SIZE, 5);
        InOrder order = inOrder(profileRepository, masterDataEventPublisher);
        order.verify(profileRepository).findAllByIdForMessaging(anyCollection());
        order.verify(masterDataEventPublisher, times(CopiedDataRepublisher.BATCH_SIZE))
                .publish(any(Object.class), any(MasterDataOperation.class));
        verify(masterDataEventPublisher, times(ids.size())).publish(any(Object.class), any(MasterDataOperation.class));
    }
}
