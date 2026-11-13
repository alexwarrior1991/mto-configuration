package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository.PackageIds;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeOperation;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeTracker.Change;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * La clave natural del seccionador, que no cabe en un indice: su nombre dentro de su paquete. Aqui
 * se fija que la regla pregunta con la consulta del importador
 * ({@code DisconnectorRepository.findByNameInPackage}) por cada paquete del seccionador escrito, que
 * el propio seccionador no cuenta como repetido y que solo mira seccionadores que no se borran.
 * Contra PostgreSQL, por los caminos de la API, lo prueba {@code NaturalKeyIT}.
 */
@ExtendWith(MockitoExtension.class)
class DisconnectorNaturalKeyGuardTest {

    private static final long STATION_PACKAGE = 1L;
    private static final long TRACK_PACKAGE = 2L;

    @Mock
    private DisconnectorRepository repository;

    @InjectMocks
    private DisconnectorNaturalKeyGuard guard;

    private static Disconnector seccionador(Long id, String name) {
        Disconnector disconnector = new Disconnector();
        disconnector.setId(id);
        disconnector.setName(name);
        return disconnector;
    }

    private void paquetes(Long id, Long station, Long poleTrack, Long track) {
        PackageIds ids = mock(PackageIds.class);
        when(ids.getStationPackageId()).thenReturn(station);
        when(ids.getPoleTrackPackageId()).thenReturn(poleTrack);
        when(ids.getTrackPackageId()).thenReturn(track);
        when(repository.findPackageIdsById(id)).thenReturn(Optional.of(ids));
    }

    @Test
    @DisplayName("un seccionador cuyo nombre solo lleva el en su paquete pasa")
    void unico() {
        Disconnector escrito = seccionador(10L, "S-12");
        paquetes(10L, STATION_PACKAGE, null, null);
        when(repository.findByNameInPackage("S-12", STATION_PACKAGE)).thenReturn(List.of(escrito));

        assertThatCode(() -> guard.afterFlush(List.of(new Change(escrito, EntityChangeOperation.CREATED))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("si otro vivo de su paquete lleva su nombre, BUS-002 sobre name")
    void repetido() {
        Disconnector escrito = seccionador(10L, "S-12");
        paquetes(10L, STATION_PACKAGE, null, null);
        when(repository.findByNameInPackage("S-12", STATION_PACKAGE))
                .thenReturn(List.of(seccionador(4L, "s-12"), escrito));

        assertThatThrownBy(() -> guard.afterFlush(List.of(new Change(escrito, EntityChangeOperation.UPDATED))))
                .isInstanceOfSatisfying(BaseException.class, e -> assertThat(e.getErrors())
                        .singleElement()
                        .satisfies(alert -> {
                            assertThat(alert.getMessage()).isEqualTo(ErrorCodes.DUPLICATED_RESOURCE);
                            assertThat(alert.getFields()).containsExactly("name");
                        }));
    }

    @Test
    @DisplayName("se pregunta en cada paquete que lo situa (estacion, via del poste, via propia), una vez por paquete")
    void cadaPaquete() {
        Disconnector escrito = seccionador(10L, "S-12");
        paquetes(10L, STATION_PACKAGE, STATION_PACKAGE, TRACK_PACKAGE);
        when(repository.findByNameInPackage("S-12", STATION_PACKAGE)).thenReturn(List.of(escrito));
        when(repository.findByNameInPackage("S-12", TRACK_PACKAGE))
                .thenReturn(List.of(escrito, seccionador(11L, "S-12")));

        assertThatThrownBy(() -> guard.afterFlush(List.of(new Change(escrito, EntityChangeOperation.CREATED))))
                .isInstanceOf(BaseException.class);

        verify(repository).findPackageIdsById(10L);
        verify(repository).findByNameInPackage("S-12", STATION_PACKAGE);
        verify(repository).findByNameInPackage("S-12", TRACK_PACKAGE);
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("el que se borra no se comprueba, ni lo que no es un seccionador")
    void loQueNoSeMira() {
        guard.afterFlush(List.of(
                new Change(seccionador(10L, "S-12"), EntityChangeOperation.DELETED),
                new Change(new Station(), EntityChangeOperation.UPDATED)));

        verifyNoInteractions(repository);
    }
}
