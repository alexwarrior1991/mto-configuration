package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.JobItemErrorDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Contadores de un trabajo en curso, con volcado periodico a la base de datos.
 *
 * <p>Los contadores viven en memoria y solo bajan a la tabla cada
 * {@code app.jobs.profile.progress-flush-interval}, que es un <b>tiempo</b> y no un numero de
 * elementos. La diferencia importa: un elemento de una carga masiva es una transaccion completa,
 * de modo que un UPDATE cada 25 es un 4% de coste; un elemento de una exportacion es una linea de
 * un CSV, y ese mismo 25 significaba cuatro mil escrituras —cada una con su transaccion propia—
 * para exportar una via de cien mil perfiles. Acotando por tiempo, un unico valor sirve para las
 * dos clases de trabajo, y el numero de escrituras deja de depender del tamano del trabajo.</p>
 *
 * <p>No es thread-safe, y no necesita serlo: cada trabajo lo recorre un unico hilo de fondo. Lo que
 * si esta protegido es el volcado, que se hace dentro de un {@code try/catch}: <b>un fallo
 * escribiendo el progreso no puede tumbar el trabajo</b>. Perder un contador es una molestia;
 * perder por eso una carga de diez mil elementos ya procesados, no.</p>
 */
@Slf4j
public class ProfileJobProgress {

    /** Cadencia de reserva si la configuracion trae un intervalo ausente o sin sentido. */
    private static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofSeconds(2);

    private final long flushIntervalNanos;
    private final int maxItemErrors;
    private final int maxMessageLength;
    private final Consumer<ProfileJobProgress> flusher;

    /**
     * Reloj monotono. {@code nanoTime} y no {@code currentTimeMillis} porque aqui solo se miden
     * intervalos, y el reloj de pared puede saltar hacia atras con un ajuste de NTP y dejar el
     * volcado congelado hasta que el tiempo lo alcance. Es inyectable para que las pruebas puedan
     * mover el reloj a mano en vez de dormir.
     */
    private final LongSupplier nanoTime;

    private final List<JobItemErrorDTO> itemErrors = new ArrayList<>();

    @Getter
    private Integer totalItems;
    @Getter
    private int processedItems;
    @Getter
    private int successfulItems;
    @Getter
    private int failedItems;

    /** Nombre del fichero producido, si el trabajo produce alguno. */
    @Getter
    private String outputFileName;

    private long lastFlushedAtNanos;

    ProfileJobProgress(Integer totalItems,
                       AsyncJobProperties.ProfileJobs settings,
                       Consumer<ProfileJobProgress> flusher) {
        this(totalItems, settings, flusher, System::nanoTime);
    }

    ProfileJobProgress(Integer totalItems,
                       AsyncJobProperties.ProfileJobs settings,
                       Consumer<ProfileJobProgress> flusher,
                       LongSupplier nanoTime) {
        this.totalItems = totalItems;
        this.flushIntervalNanos = intervalNanosOf(settings.getProgressFlushInterval());
        this.maxItemErrors = Math.max(0, settings.getMaxItemErrors());
        this.maxMessageLength = Math.max(1, settings.getMaxItemErrorMessageLength());
        this.flusher = flusher;
        this.nanoTime = nanoTime;

        // Se arranca el contador en el instante de creacion: sin esto el primer elemento caeria
        // siempre fuera de plazo y provocaria un volcado inmediato con los contadores a cero.
        this.lastFlushedAtNanos = nanoTime.getAsLong();
    }

    private static long intervalNanosOf(Duration interval) {
        if (interval == null || interval.isNegative() || interval.isZero()) {
            return DEFAULT_FLUSH_INTERVAL.toNanos();
        }

        return interval.toNanos();
    }

    /** Elemento procesado con exito. */
    public void itemSucceeded() {
        processedItems++;
        successfulItems++;
        flushIfDue();
    }

    /**
     * Elemento fallido.
     *
     * <p>El trabajo <b>continua</b>: un elemento invalido no invalida los otros mil. Solo se guarda
     * el detalle de los primeros {@code maxItemErrors}; a partir de ahi se sigue contando.</p>
     */
    public void itemFailed(int index, String operation, String code, String message) {
        processedItems++;
        failedItems++;

        if (itemErrors.size() < maxItemErrors) {
            itemErrors.add(new JobItemErrorDTO(index, operation, code, truncate(message)));
        }

        flushIfDue();
    }

    /** Fija el total cuando solo se conoce al terminar, como en una exportacion por ventanas. */
    void setTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
    }

    void setOutputFileName(String outputFileName) {
        this.outputFileName = outputFileName;
    }

    public List<JobItemErrorDTO> getItemErrors() {
        return List.copyOf(itemErrors);
    }

    /** Vuelca los contadores ahora, pase lo que pase con el intervalo. */
    void flush() {
        lastFlushedAtNanos = nanoTime.getAsLong();

        try {
            flusher.accept(this);
        } catch (Exception e) {
            // A proposito no se propaga: el progreso es informativo y el trabajo real ya esta
            // hecho. Dejar que un fallo al escribir un contador aborte la carga seria cambiar un
            // problema cosmetico por la perdida del trabajo.
            log.warn("No se pudo guardar el progreso del trabajo ({} procesados)", processedItems, e);
        }
    }

    private void flushIfDue() {
        if (nanoTime.getAsLong() - lastFlushedAtNanos >= flushIntervalNanos) {
            flush();
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }

        return message.length() <= maxMessageLength
                ? message
                : message.substring(0, maxMessageLength) + "…";
    }
}
