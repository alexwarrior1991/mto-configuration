package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El tope de concurrencia es la unica pieza que impide que los trabajos de fondo vacien el pool de
 * conexiones. Lo que se fija aqui es que el limite se respete, que el permiso vuelva y que no se
 * pueda inflar devolviendolo dos veces.
 */
class ProfileJobConcurrencyGuardTest {

    private ProfileJobConcurrencyGuard guardWith(int exportLimit, int bulkLimit) {
        AsyncJobProperties properties = new AsyncJobProperties();
        properties.getProfile().setExportMaxConcurrency(exportLimit);
        properties.getProfile().setBulkMaxConcurrency(bulkLimit);

        return new ProfileJobConcurrencyGuard(properties);
    }

    @Test
    @DisplayName("concede permisos hasta el tope y rechaza el siguiente")
    void concedeHastaElTope() {
        ProfileJobConcurrencyGuard guard = guardWith(2, 1);

        assertThat(guard.tryAcquire(JobType.PROFILE_EXPORT)).isPresent();
        assertThat(guard.tryAcquire(JobType.PROFILE_EXPORT)).isPresent();

        assertThat(guard.tryAcquire(JobType.PROFILE_EXPORT))
                .as("el tercero no cabe con export-max-concurrency=2")
                .isEmpty();
    }

    @Test
    @DisplayName("exportaciones y cargas masivas no comparten tope")
    void toposIndependientes() {
        ProfileJobConcurrencyGuard guard = guardWith(1, 1);

        assertThat(guard.tryAcquire(JobType.PROFILE_EXPORT)).isPresent();

        // Una exportacion en curso no puede estorbar a una carga: compiten por recursos distintos.
        assertThat(guard.tryAcquire(JobType.PROFILE_BULK_CREATE)).isPresent();
        assertThat(guard.tryAcquire(JobType.PROFILE_BULK_UPDATE))
                .as("las dos clases de carga masiva comparten el mismo semaforo")
                .isEmpty();
    }

    @Test
    @DisplayName("cerrar el permiso devuelve el hueco")
    void cerrarDevuelveElHueco() {
        ProfileJobConcurrencyGuard guard = guardWith(1, 1);

        JobPermit permit = guard.tryAcquire(JobType.PROFILE_EXPORT).orElseThrow();
        assertThat(guard.tryAcquire(JobType.PROFILE_EXPORT)).isEmpty();

        permit.close();

        assertThat(guard.tryAcquire(JobType.PROFILE_EXPORT)).isPresent();
    }

    @Test
    @DisplayName("cerrar dos veces no añade permisos de la nada")
    void cierreIdempotente() {
        ProfileJobConcurrencyGuard guard = guardWith(1, 1);

        JobPermit permit = guard.tryAcquire(JobType.PROFILE_EXPORT).orElseThrow();
        permit.close();
        permit.close();

        // Sin la guarda de idempotencia el semaforo tendria DOS permisos y el tope habria dejado
        // de existir en silencio. Es un fallo que no da la cara hasta que un dia corren veinte
        // exportaciones a la vez.
        assertThat(guard.availablePermits(JobType.PROFILE_EXPORT)).isEqualTo(1);

        Optional<JobPermit> reacquired = guard.tryAcquire(JobType.PROFILE_EXPORT);
        assertThat(reacquired).isPresent();
        assertThat(guard.tryAcquire(JobType.PROFILE_EXPORT)).isEmpty();
    }
}
