package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.Semaphore;

/**
 * Tope de trabajos simultaneos, por clase de trabajo.
 *
 * <p>Que la ejecucion sea en hilos virtuales no quita la necesidad de un tope, mas bien al
 * contrario: crear diez mil hilos virtuales es trivial y gratis, y justamente por eso nada frena
 * por si solo a diez mil trabajos peleandose por las diez conexiones de HikariCP. El recurso escaso
 * no es el hilo, es la conexion a PostgreSQL —y en la exportacion, tambien el disco—. El semaforo
 * pone el limite donde esta el recurso.</p>
 *
 * <p>Exportaciones y cargas masivas tienen semaforos <b>separados</b> porque no compiten por lo
 * mismo: una exportacion es una lectura larga y una carga masiva es una rafaga de escrituras.
 * Compartir un unico tope habria hecho que una exportacion en curso bloqueara una carga que no le
 * estorbaba.</p>
 *
 * <p>La adquisicion nunca espera ({@code tryAcquire} sin plazo). Encolar la peticion HTTP hasta que
 * haya hueco seria volver al problema que esta capa viene a resolver: el cliente esperando a que el
 * servidor tenga tiempo. Sin hueco se responde ya, y se responde que no.</p>
 */
@Slf4j
@Component
public class ProfileJobConcurrencyGuard {

    private final Semaphore exportPermits;
    private final Semaphore bulkPermits;

    public ProfileJobConcurrencyGuard(AsyncJobProperties properties) {
        AsyncJobProperties.ProfileJobs profileJobs = properties.getProfile();

        this.exportPermits = new Semaphore(Math.max(1, profileJobs.getExportMaxConcurrency()));
        this.bulkPermits = new Semaphore(Math.max(1, profileJobs.getBulkMaxConcurrency()));

        log.info("Topes de trabajos de perfiles: export={}, bulk={}",
                exportPermits.availablePermits(), bulkPermits.availablePermits());
    }

    /**
     * Reserva un hueco para {@code type}, o devuelve vacio si no lo hay.
     *
     * <p>Quien recibe el permiso es responsable de cerrarlo, y de hacerlo en un {@code finally}:
     * un trabajo que falle sin devolverlo consume el hueco para siempre.</p>
     */
    public Optional<JobPermit> tryAcquire(JobType type) {
        String name = type.name();
        Semaphore semaphore = semaphoreFor(type);

        if (!semaphore.tryAcquire()) {
            log.warn("Sin capacidad para un trabajo [{}]: todos los permisos estan en uso", name);
            return Optional.empty();
        }

        log.debug("Permiso de ejecucion concedido [{}], disponibles={}", name, semaphore.availablePermits());
        return Optional.of(new JobPermit(name, semaphore));
    }

    /** Permisos libres de esa clase de trabajo. Para metricas y tests; no sirve para decidir nada. */
    public int availablePermits(JobType type) {
        return semaphoreFor(type).availablePermits();
    }

    private Semaphore semaphoreFor(JobType type) {
        return switch (type) {
            case PROFILE_EXPORT -> exportPermits;
            case PROFILE_BULK_CREATE, PROFILE_BULK_UPDATE -> bulkPermits;
        };
    }
}
