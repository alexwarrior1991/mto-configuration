package com.alejandro.mtoconfiguration.jobs;

import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobSlotGroup;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.repository.jpa.jobs.AsyncJobRepository;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.AsyncJobProperties;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.AsyncJobStore;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reparto de cupo contra PostgreSQL de verdad.
 *
 * <p>Esto NO se puede probar con dobles. Lo que se verifica es que «cuenta los que hay y, si caben,
 * mete uno» sea atomico, y esa propiedad solo existe si el cerrojo consultivo y la transaccion se
 * comportan como se espera: un {@code AsyncJobStore} simulado diria que si a cualquier cosa.</p>
 *
 * <p>Tampoco vale H2: {@code pg_advisory_xact_lock} es de PostgreSQL, y el proyecto ya levanta un
 * PostgreSQL real para el resto de tests de integracion.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AsyncJobStore.class, AsyncJobSlotIT.SlotTestConfiguration.class})
class AsyncJobSlotIT {

    @Autowired
    private AsyncJobStore store;

    @Autowired
    private AsyncJobRepository repository;

    @Autowired
    private AsyncJobProperties properties;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
    }

    @BeforeEach
    @AfterEach
    void limpiar() {
        repository.deleteAll();

        properties.getProfile().setExportMaxConcurrency(2);
        properties.getProfile().setBulkMaxConcurrency(1);
        properties.getHeartbeat().setTimeout(Duration.ofMinutes(2));
    }

    private AsyncJob claimExport() {
        return store.createClaimingSlot(JobType.PROFILE_EXPORT, 1L, "basic", null, "ana");
    }

    @Test
    @DisplayName("concede cupo hasta el tope y rechaza el siguiente")
    void concedeHastaElTope() {
        assertThat(claimExport().getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(claimExport().getStatus()).isEqualTo(JobStatus.PENDING);

        AsyncJob rechazado = claimExport();

        assertThat(rechazado.getStatus()).isEqualTo(JobStatus.REJECTED);
        // El rechazo deja fila para que sea observable, y nace ya terminado: no habra un despues.
        assertThat(rechazado.getFinishedAt()).isNotNull();
        assertThat(rechazado.getErrorMessage()).contains("Sin capacidad");
    }

    @Test
    @DisplayName("exportaciones y cargas masivas no comparten cupo")
    void cuposIndependientes() {
        properties.getProfile().setExportMaxConcurrency(1);

        assertThat(claimExport().getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(claimExport().getStatus()).isEqualTo(JobStatus.REJECTED);

        // Una exportacion en curso no puede estorbar a una carga: compiten por recursos distintos.
        assertThat(store.createClaimingSlot(JobType.PROFILE_BULK_CREATE, null, null, 10, "ana")
                .getStatus()).isEqualTo(JobStatus.PENDING);
    }

    @Test
    @DisplayName("un trabajo terminado devuelve su hueco")
    void elTrabajoTerminadoDevuelveElHueco() {
        properties.getProfile().setExportMaxConcurrency(1);

        AsyncJob primero = claimExport();
        assertThat(claimExport().getStatus()).isEqualTo(JobStatus.REJECTED);

        store.markFinished(primero.getId(), JobStatus.COMPLETED, null, null);

        // El hueco no lo suelta nadie explicitamente: se deja de ocupar porque el trabajo ya no
        // esta en un estado vivo. De ahi que no exista la clase de fallo del permiso no devuelto.
        assertThat(claimExport().getStatus()).isEqualTo(JobStatus.PENDING);
    }

    @Test
    @DisplayName("un trabajo que dejo de latir no retiene su hueco")
    void elTrabajoDifuntoNoRetieneElHueco() {
        properties.getProfile().setExportMaxConcurrency(1);

        AsyncJob zombi = claimExport();
        assertThat(claimExport().getStatus()).isEqualTo(JobStatus.REJECTED);

        // Se simula la muerte de la replica: la fila sigue viva pero el latido se enfria.
        enfriarLatido(zombi, Duration.ofMinutes(10));

        // Esta es la propiedad que hace viable el tope de clúster. Sin ella, una replica que muere
        // a mitad de una exportacion reduce el tope de forma permanente, y el sintoma aparece mucho
        // despues de la causa sin apuntar a ella.
        assertThat(claimExport().getStatus()).isEqualTo(JobStatus.PENDING);
    }

    @Test
    @DisplayName("el reaper cierra el difunto, pero el hueco ya estaba libre antes")
    void elReaperSoloCorrigeElEstado() {
        AsyncJob zombi = claimExport();
        enfriarLatido(zombi, Duration.ofMinutes(10));

        Instant now = Instant.now();
        int cerrados = repository.failStale(AsyncJobRepository.ACTIVE_STATUSES, JobStatus.FAILED,
                now.minus(properties.getHeartbeat().getTimeout()), now, "sin señales");

        assertThat(cerrados).isEqualTo(1);
        assertThat(repository.findById(zombi.getId()).orElseThrow().getStatus())
                .isEqualTo(JobStatus.FAILED);
    }

    @Test
    @DisplayName("dos peticiones a la vez no se cuelan las dos por el mismo hueco")
    void sinCarreraConPeticionesSimultaneas() throws Exception {
        properties.getProfile().setBulkMaxConcurrency(1);

        int peticiones = 8;

        // Sin el cerrojo consultivo esto es una condicion de carrera de manual: las ocho leen el
        // mismo recuento a cero, las ocho ven hueco y las ocho entran. Con un tope de uno, eso son
        // ocho cargas masivas simultaneas, justo lo que el tope existe para impedir.
        try (ExecutorService pool = Executors.newFixedThreadPool(peticiones)) {
            List<Callable<JobStatus>> peticionesSimultaneas = IntStream.range(0, peticiones)
                    .<Callable<JobStatus>>mapToObj(i -> () ->
                            store.createClaimingSlot(JobType.PROFILE_BULK_CREATE, null, null, 5, "ana")
                                    .getStatus())
                    .toList();

            List<JobStatus> resultados = pool.invokeAll(peticionesSimultaneas).stream()
                    .map(AsyncJobSlotIT::valueOf)
                    .toList();

            assertThat(resultados).filteredOn(JobStatus.PENDING::equals).hasSize(1);
            assertThat(resultados).filteredOn(JobStatus.REJECTED::equals).hasSize(peticiones - 1);
        }
    }

    @Test
    @DisplayName("el recuento de ocupacion es el mismo que decide el reparto")
    void laOcupacionPublicadaEsLaReal() {
        claimExport();
        claimExport();

        // Las metricas salen de aqui: lo que se ve en el panel es exactamente lo que aplica el
        // tope, y no una aproximacion calculada por otro camino.
        assertThat(store.countAlive(JobSlotGroup.EXPORT)).isEqualTo(2);
        assertThat(store.countAlive(JobSlotGroup.BULK)).isZero();
        assertThat(store.maxConcurrencyOf(JobSlotGroup.EXPORT)).isEqualTo(2);
    }

    private void enfriarLatido(AsyncJob job, Duration antiguedad) {
        AsyncJob almacenado = repository.findById(job.getId()).orElseThrow();
        almacenado.setHeartbeatAt(Instant.now().minus(antiguedad));
        repository.saveAndFlush(almacenado);
    }

    private static JobStatus valueOf(Future<JobStatus> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @TestConfiguration
    static class SlotTestConfiguration {

        @Bean
        AsyncJobProperties asyncJobProperties() {
            return new AsyncJobProperties();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
