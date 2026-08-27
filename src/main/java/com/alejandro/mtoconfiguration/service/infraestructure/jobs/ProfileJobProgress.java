package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.JobItemErrorDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Contadores de un trabajo en curso, con volcado periodico a la base de datos.
 *
 * <p>Los contadores viven en memoria y solo bajan a la tabla cada
 * {@code app.jobs.profile.progress-flush-interval} elementos. Escribir en cada elemento habria
 * duplicado las escrituras de la carga entera —un UPDATE por cada INSERT— solo para mover un
 * numero que nadie mira con esa resolucion.</p>
 *
 * <p>No es thread-safe, y no necesita serlo: cada trabajo lo recorre un unico hilo de fondo. Lo que
 * si esta protegido es el volcado, que se hace dentro de un {@code try/catch}: <b>un fallo
 * escribiendo el progreso no puede tumbar el trabajo</b>. Perder un contador es una molestia;
 * perder por eso una carga de diez mil elementos ya procesados, no.</p>
 */
@Slf4j
public class ProfileJobProgress {

    private final int flushInterval;
    private final int maxItemErrors;
    private final int maxMessageLength;
    private final Consumer<ProfileJobProgress> flusher;

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

    private int lastFlushedAt;

    ProfileJobProgress(Integer totalItems,
                       AsyncJobProperties.ProfileJobs settings,
                       Consumer<ProfileJobProgress> flusher) {
        this.totalItems = totalItems;
        this.flushInterval = Math.max(1, settings.getProgressFlushInterval());
        this.maxItemErrors = Math.max(0, settings.getMaxItemErrors());
        this.maxMessageLength = Math.max(1, settings.getMaxItemErrorMessageLength());
        this.flusher = flusher;
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
        lastFlushedAt = processedItems;

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
        if (processedItems - lastFlushedAt >= flushInterval) {
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
