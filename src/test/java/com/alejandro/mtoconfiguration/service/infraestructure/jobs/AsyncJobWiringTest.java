package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.repository.jpa.jobs.AsyncJobRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Cableado real de Spring para la capa de trabajos.
 *
 * <p>Aqui se ve lo que no ve ningun test con dobles sueltos: un {@code @ConditionalOnProperty} mal
 * escrito, un nombre de propiedad que no enlaza, un bean que no llega a crearse. Son fallos que no
 * dan la cara al compilar ni en los tests unitarios —cada clase por separado funciona— y que
 * aparecen al arrancar la aplicacion, o peor: no aparecen nunca y simplemente algo deja de correr.</p>
 *
 * <p>El caso concreto que motiva esto: la purga y el reaper son planificados. Si su condicion o su
 * planificador estuvieran mal, no fallaria nada visible; la tabla y el disco se limitarian a crecer
 * en silencio hasta que alguien se quedara sin espacio meses despues.</p>
 *
 * <p>{@code AsyncJobStore} se queda fuera a proposito: necesita un {@code EntityManager} de verdad
 * y ya esta cubierto contra PostgreSQL en {@code AsyncJobSlotIT}, que es una prueba mas fuerte que
 * cualquier cosa que se pudiera hacer aqui.</p>
 */
class AsyncJobWiringTest {

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        AsyncJobConfiguration.class,
                        AsyncJobHeartbeat.class,
                        AsyncJobReaper.class,
                        ProfileJobFiles.class,
                        AsyncJobPurgeService.class,
                        AsyncJobPurgeScheduler.class,
                        AsyncJobMetricsScheduler.class)
                // En produccion lo aporta Actuator; aqui basta un registro en memoria.
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                // El acceso a base de datos no entra: lo que se comprueba es el cableado.
                .withBean(AsyncJobRepository.class, () -> mock(AsyncJobRepository.class))
                // El doble de AsyncJobStore hereda su campo @PersistenceContext, y Spring intenta
                // inyectarlo igualmente. Con una factoria falsa se resuelve a un proxy perezoso que
                // nadie llega a usar, que es justo lo que hace falta para probar el cableado.
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withBean(AsyncJobStore.class, () -> mock(AsyncJobStore.class));
    }

    @Test
    @DisplayName("la capa arranca entera con los valores por defecto")
    void laCapaArrancaEntera() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();

            assertThat(context).hasSingleBean(AsyncJobProperties.class);
            assertThat(context).hasSingleBean(AsyncJobMetrics.class);
            assertThat(context).hasSingleBean(AsyncJobHeartbeat.class);
            assertThat(context).hasSingleBean(AsyncJobReaper.class);
            assertThat(context).hasSingleBean(AsyncJobPurgeService.class);
            assertThat(context).hasSingleBean(AsyncJobPurgeScheduler.class);
            assertThat(context).hasSingleBean(AsyncJobMetricsScheduler.class);
            assertThat(context).hasSingleBean(ProfileJobFiles.class);

            AsyncJobProperties properties = context.getBean(AsyncJobProperties.class);

            assertThat(properties.getProfile().getExportMaxConcurrency()).isEqualTo(2);
            assertThat(properties.getProfile().getBulkMaxConcurrency()).isEqualTo(1);
            assertThat(properties.getHeartbeat().getInterval()).isEqualTo(Duration.ofSeconds(15));
            assertThat(properties.getHeartbeat().getTimeout()).isEqualTo(Duration.ofMinutes(2));
            assertThat(properties.getPurge().getRetention()).isEqualTo(Duration.ofDays(7));
        });
    }

    @Test
    @DisplayName("los ajustes del yaml llegan a las propiedades")
    void losAjustesSeEnlazan() {
        runner().withPropertyValues(
                        "app.jobs.profile.export-max-concurrency=5",
                        "app.jobs.profile.bulk-max-concurrency=3",
                        "app.jobs.profile.max-bulk-items=1000",
                        "app.jobs.profile.export-directory=/datos/exports",
                        "app.jobs.profile.progress-flush-interval=10s",
                        "app.jobs.profile.max-item-error-message-length=120",
                        "app.jobs.heartbeat.timeout=5m",
                        "app.jobs.purge.retention=30d")
                .run(context -> {
                    AsyncJobProperties properties = context.getBean(AsyncJobProperties.class);

                    // Un nombre mal escrito no falla: se queda con el valor por defecto y el ajuste
                    // del yaml no hace nada. Eso es exactamente lo que pasaria sin este test.
                    assertThat(properties.getProfile().getExportMaxConcurrency()).isEqualTo(5);
                    assertThat(properties.getProfile().getBulkMaxConcurrency()).isEqualTo(3);
                    assertThat(properties.getProfile().getMaxBulkItems()).isEqualTo(1_000);
                    assertThat(properties.getProfile().getExportDirectory()).isEqualTo(Path.of("/datos/exports"));
                    assertThat(properties.getProfile().getProgressFlushInterval()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(properties.getProfile().getMaxItemErrorMessageLength()).isEqualTo(120);
                    assertThat(properties.getHeartbeat().getTimeout()).isEqualTo(Duration.ofMinutes(5));
                    assertThat(properties.getPurge().getRetention()).isEqualTo(Duration.ofDays(30));
                });
    }

    @Test
    @DisplayName("la purga se puede apagar, y viene encendida si nadie dice nada")
    void laPurgaSePuedeApagar() {
        runner().withPropertyValues("app.jobs.purge.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AsyncJobPurgeScheduler.class));

        // matchIfMissing: sin la propiedad la purga corre. Al reves —apagada por omision— la tabla
        // y el directorio de exportacion creceran para siempre sin que nada lo advierta.
        runner().run(context -> assertThat(context).hasSingleBean(AsyncJobPurgeScheduler.class));
    }
}
