package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.enums.jobs.MasterDataRepublishTarget;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEventPublisher;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataOperation;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.JobItemErrorDTO;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El recorrido del republicado.
 *
 * <p>Lo que se fija aqui es que el evento republicado sea <b>indistinguible</b> del de una edicion
 * real: {@code UPDATED}, una publicacion por entidad, y la entidad releida con
 * {@code findByIdForMessaging} —el metodo con {@code @EntityGraph}— y no con {@code findById}. Con
 * {@code findById} el payload saldria con relaciones sin inicializar o con un select por relacion,
 * y el consumidor recibiria algo distinto de lo que recibe cuando alguien edita.</p>
 *
 * <p>El publicador de lotes es real y no un doble: es el que traduce ids a eventos, asi que
 * sustituirlo dejaria la prueba sin nada que comprobar. Lo que se simula es su {@code @Transactional},
 * que aqui no aplica porque no hay proxy de Spring; la transaccion de verdad se prueba en
 * {@code MasterDataRepublishIT}.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MasterDataRepublishJobRunnerTest {

    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-00000000ab12");

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private DisconnectorRepository disconnectorRepository;
    @Mock
    private SectionInsulatorRepository sectionInsulatorRepository;
    @Mock
    private MasterDataEventPublisher publisher;

    private AsyncJobProperties properties;
    private MasterDataRepublishJobRunner runner;

    @BeforeEach
    void setUp() {
        properties = new AsyncJobProperties();
        properties.getRepublish().setBatchSize(2);

        runner = new MasterDataRepublishJobRunner(profileRepository, disconnectorRepository,
                sectionInsulatorRepository, new MasterDataRepublishBatchPublisher(publisher), properties);
    }

    private ProfileJobProgress newProgress(Integer total) {
        return new ProfileJobProgress(total, properties.getProfile(), p -> { });
    }

    private Profile profile(Long id) {
        Profile profile = new Profile();
        profile.setId(id);
        return profile;
    }

    private Disconnector disconnector(Long id) {
        Disconnector disconnector = new Disconnector();
        disconnector.setId(id);
        return disconnector;
    }

    private SectionInsulator sectionInsulator(Long id) {
        SectionInsulator insulator = new SectionInsulator();
        insulator.setId(id);
        return insulator;
    }

    /** Devuelve las paginas en orden y una vacia al final, como haria el keyset real. */
    private void profilePages(Long trackId, List<List<Long>> pages) {
        List<List<Long>> remaining = new ArrayList<>(pages);
        remaining.add(List.of());

        when(profileRepository.findIdsForRepublish(eq(trackId), any(), any(Pageable.class)))
                .thenAnswer(invocation -> remaining.isEmpty() ? List.of() : remaining.removeFirst());

        when(profileRepository.findByIdForMessaging(any()))
                .thenAnswer(invocation -> Optional.of(profile(invocation.getArgument(0))));
    }

    @Test
    @DisplayName("publica un evento UPDATED por perfil, releido con el grafo de mensajeria")
    void publicaUnEventoPorPerfil() {
        profilePages(null, List.of(List.of(1L, 2L)));
        ProfileJobProgress progress = newProgress(2);

        runner.run(JOB_ID, MasterDataRepublishTarget.PROFILE, null, null, progress);

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(publisher, org.mockito.Mockito.times(2))
                .publish(published.capture(), eq(MasterDataOperation.UPDATED));

        assertThat(published.getAllValues())
                .extracting(entity -> ((Profile) entity).getId())
                .containsExactly(1L, 2L);

        // El grafo de mensajeria es lo que hace que el payload republicado sea identico al de una
        // edicion real; findById daria otro.
        verify(profileRepository).findByIdForMessaging(1L);
        verify(profileRepository).findByIdForMessaging(2L);
        verify(profileRepository, never()).findById(any());

        assertThat(progress.getSuccessfulItems()).isEqualTo(2);
        assertThat(progress.getFailedItems()).isZero();
    }

    @Test
    @DisplayName("recorre por paginas hasta que una vuelve vacia, avanzando el ultimo id")
    void recorrePorPaginas() {
        profilePages(null, List.of(List.of(1L, 2L), List.of(7L, 9L), List.of(11L)));
        ProfileJobProgress progress = newProgress(5);

        runner.run(JOB_ID, MasterDataRepublishTarget.PROFILE, null, null, progress);

        assertThat(progress.getSuccessfulItems()).isEqualTo(5);

        // El keyset avanza con el ultimo id de cada pagina: sin eso el recorrido se quedaria
        // repitiendo la primera pagina para siempre.
        ArgumentCaptor<Long> lastId = ArgumentCaptor.forClass(Long.class);
        verify(profileRepository, org.mockito.Mockito.atLeast(4))
                .findIdsForRepublish(isNull(), lastId.capture(), any(Pageable.class));
        assertThat(lastId.getAllValues()).startsWith(0L, 2L, 9L, 11L);
    }

    @Test
    @DisplayName("el filtro por via se traslada a la consulta de identificadores")
    void filtroPorVia() {
        profilePages(7L, List.of(List.of(3L)));
        ProfileJobProgress progress = newProgress(1);

        runner.run(JOB_ID, MasterDataRepublishTarget.PROFILE, 7L, null, progress);

        verify(profileRepository).findIdsForRepublish(eq(7L), eq(0L), any(Pageable.class));
        verify(publisher).publish(any(Profile.class), eq(MasterDataOperation.UPDATED));
    }

    @Test
    @DisplayName("el filtro por estacion acota seccionadores y aisladores")
    void filtroPorEstacion() {
        when(disconnectorRepository.findIdsForRepublish(eq(4L), any(), any(Pageable.class)))
                .thenReturn(List.of(20L), List.of());
        when(disconnectorRepository.findByIdForMessaging(20L)).thenReturn(Optional.of(disconnector(20L)));

        ProfileJobProgress progress = newProgress(1);
        runner.run(JOB_ID, MasterDataRepublishTarget.DISCONNECTOR, null, 4L, progress);

        verify(disconnectorRepository).findIdsForRepublish(eq(4L), eq(0L), any(Pageable.class));
        verify(publisher).publish(any(Disconnector.class), eq(MasterDataOperation.UPDATED));

        // Un seccionador no acota por via: no la tiene. Si el runner le pasara el trackId, el
        // filtro se aplicaria sobre la estacion y devolveria cualquier cosa.
        verify(profileRepository, never()).findIdsForRepublish(any(), any(), any(Pageable.class));
        assertThat(progress.getSuccessfulItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("all recorre los tres tipos, cada uno con su repositorio")
    void allRecorreLosTresTipos() {
        when(profileRepository.findIdsForRepublish(isNull(), any(), any(Pageable.class)))
                .thenReturn(List.of(1L), List.of());
        when(profileRepository.findByIdForMessaging(1L)).thenReturn(Optional.of(profile(1L)));
        when(disconnectorRepository.findIdsForRepublish(isNull(), any(), any(Pageable.class)))
                .thenReturn(List.of(2L), List.of());
        when(disconnectorRepository.findByIdForMessaging(2L)).thenReturn(Optional.of(disconnector(2L)));
        when(sectionInsulatorRepository.findIdsForRepublish(isNull(), any(), any(Pageable.class)))
                .thenReturn(List.of(3L), List.of());
        when(sectionInsulatorRepository.findByIdForMessaging(3L))
                .thenReturn(Optional.of(sectionInsulator(3L)));

        ProfileJobProgress progress = newProgress(3);
        runner.run(JOB_ID, MasterDataRepublishTarget.ALL, null, null, progress);

        verify(publisher).publish(any(Profile.class), eq(MasterDataOperation.UPDATED));
        verify(publisher).publish(any(Disconnector.class), eq(MasterDataOperation.UPDATED));
        verify(publisher).publish(any(SectionInsulator.class), eq(MasterDataOperation.UPDATED));
        assertThat(progress.getSuccessfulItems()).isEqualTo(3);
    }

    @Test
    @DisplayName("un elemento borrado entre la pagina y la lectura se cuenta y no detiene el recorrido")
    void elementoDesaparecido() {
        profilePages(null, List.of(List.of(1L, 2L)));
        when(profileRepository.findByIdForMessaging(1L)).thenReturn(Optional.empty());

        ProfileJobProgress progress = newProgress(2);
        runner.run(JOB_ID, MasterDataRepublishTarget.PROFILE, null, null, progress);

        assertThat(progress.getSuccessfulItems()).isEqualTo(1);
        assertThat(progress.getFailedItems()).isEqualTo(1);

        // Se cuenta, y no se ignora, para que el recuento cuadre con el total anunciado en el 202.
        JobItemErrorDTO error = progress.getItemErrors().getFirst();
        assertThat(error.index()).isZero();
        assertThat(error.operation()).isEqualTo("profile");
        assertThat(error.code()).isEqualTo("NotFound");
    }

    @Test
    @DisplayName("un lote que no se confirma cuenta entero como fallido y el recorrido sigue")
    void loteFallidoNoSeCuentaComoExitoParcial() {
        profilePages(null, List.of(List.of(1L, 2L), List.of(3L, 4L)));
        doThrow(new IllegalStateException("outbox caido")).when(publisher).publish(any(), any());

        ProfileJobProgress progress = newProgress(4);
        runner.run(JOB_ID, MasterDataRepublishTarget.PROFILE, null, null, progress);

        // El lote es una transaccion: no hay exito parcial que contar. Dar por buenos los que iban
        // antes del fallo seria contar eventos que nunca llegaron a existir.
        assertThat(progress.getSuccessfulItems()).isZero();
        assertThat(progress.getFailedItems()).isEqualTo(4);
        assertThat(progress.getItemErrors())
                .extracting(JobItemErrorDTO::index)
                .containsExactly(0, 1, 2, 3);
    }
}
