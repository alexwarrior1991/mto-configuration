package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.enums.jobs.JobSlotGroup;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metricas de los trabajos en segundo plano.
 *
 * <p>Sin esto, el rechazo por falta de cupo es invisible: la aplicacion responde 429, deja su fila
 * REJECTED y nadie se entera hasta que un usuario se queja de que «no le deja exportar». La señal
 * que hay que vigilar es {@code mto.jobs.submitted.total{outcome="rejected"}}: si sube de forma
 * sostenida, los topes se han quedado cortos para el uso real —o hay trabajos zombis ocupando
 * sitio, que es lo que cuenta {@code mto.jobs.reaped.total}—.</p>
 *
 * <p>Los contadores se llevan en memoria y los gauges se refrescan desde una foto periodica, no en
 * cada scrape: un gauge que consulta la base de datos cada vez que Prometheus pregunta convierte la
 * observabilidad en carga. Mismo criterio que en {@code OutboxMetrics}.</p>
 *
 * <p>Los gauges de ocupacion son de <b>clúster</b>, no de la replica: salen del mismo recuento que
 * decide si hay hueco, asi que lo que se ve en el panel es exactamente lo que aplica el tope.</p>
 */
public class AsyncJobMetrics {

    // Publicos porque son contrato: los paneles y las alertas se escriben contra estos nombres, asi
    // que cambiarlos rompe cuadros de mando fuera de este repositorio.
    public static final String SUBMITTED_COUNT = "mto.jobs.submitted.total";
    public static final String FINISHED_COUNT = "mto.jobs.finished.total";
    public static final String REAPED_COUNT = "mto.jobs.reaped.total";
    public static final String DURATION = "mto.jobs.duration";
    public static final String ACTIVE_COUNT = "mto.jobs.active";
    public static final String SLOTS_MAX = "mto.jobs.slots.max";

    private final MeterRegistry meterRegistry;

    private final Map<JobSlotGroup, AtomicLong> active = new EnumMap<>(JobSlotGroup.class);
    private final Map<JobSlotGroup, AtomicLong> slots = new EnumMap<>(JobSlotGroup.class);

    private final Counter reaped;

    public AsyncJobMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        for (JobSlotGroup group : JobSlotGroup.values()) {
            AtomicLong activeGauge = new AtomicLong();
            AtomicLong slotsGauge = new AtomicLong();

            active.put(group, activeGauge);
            slots.put(group, slotsGauge);

            Gauge.builder(ACTIVE_COUNT, activeGauge, AtomicLong::get)
                    .tag("group", group.name())
                    .description("Trabajos en marcha en todo el despliegue que siguen dando señales de vida")
                    .register(meterRegistry);

            // El tope se publica como metrica para que el panel pueda pintar ocupacion sobre
            // capacidad sin que nadie tenga que recordar el valor configurado.
            Gauge.builder(SLOTS_MAX, slotsGauge, AtomicLong::get)
                    .tag("group", group.name())
                    .description("Trabajos simultaneos permitidos en todo el despliegue")
                    .register(meterRegistry);
        }

        this.reaped = Counter.builder(REAPED_COUNT)
                .description("Trabajos cerrados por dejar de dar señales de vida")
                .register(meterRegistry);
    }

    public void recordAccepted(JobType type) {
        submitted(type, "accepted").increment();
    }

    public void recordRejected(JobType type) {
        submitted(type, "rejected").increment();
    }

    /** Cierre de un trabajo: cuenta el resultado y el tiempo que tardo. */
    public void recordFinished(JobType type, JobStatus status, Duration elapsed) {
        Counter.builder(FINISHED_COUNT)
                .tag("type", type.name())
                .tag("status", status.name())
                .description("Trabajos terminados, por resultado")
                .register(meterRegistry)
                .increment();

        Timer.builder(DURATION)
                .tag("type", type.name())
                .tag("status", status.name())
                .description("Duracion de los trabajos en segundo plano")
                .register(meterRegistry)
                .record(elapsed);
    }

    public void recordReaped(int count) {
        reaped.increment(count);
    }

    /** Vuelca la foto de ocupacion en los gauges. */
    public void updateOccupancy(JobSlotGroup group, long aliveJobs, int maxConcurrency) {
        active.get(group).set(aliveJobs);
        slots.get(group).set(maxConcurrency);
    }

    private Counter submitted(JobType type, String outcome) {
        return Counter.builder(SUBMITTED_COUNT)
                .tag("type", type.name())
                .tag("outcome", outcome)
                .description("Trabajos pedidos, aceptados o rechazados por falta de cupo")
                .register(meterRegistry);
    }
}
