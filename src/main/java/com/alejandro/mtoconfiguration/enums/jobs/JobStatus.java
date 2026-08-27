package com.alejandro.mtoconfiguration.enums.jobs;

/**
 * Ciclo de vida de un {@link com.alejandro.mtoconfiguration.entity.jobs.AsyncJob}.
 *
 * <pre>
 *   PENDING ──▶ RUNNING ──┬──▶ COMPLETED
 *                         ├──▶ COMPLETED_WITH_ERRORS
 *                         └──▶ FAILED
 *   REJECTED (estado terminal de entrada: el trabajo nunca llego a encolarse)
 * </pre>
 *
 * <p>Igual que {@link JobType}, el valor se persiste como texto y forma parte del contrato HTTP.
 * Anadir un estado exige ampliar la restriccion CHECK de {@code async_job.status}.</p>
 */
public enum JobStatus {

    /** Aceptado y encolado; todavia no ha empezado a ejecutarse. */
    PENDING,

    /** En ejecucion en segundo plano. El contador de progreso se refresca periodicamente. */
    RUNNING,

    /** Terminado sin ningun elemento fallido. */
    COMPLETED,

    /**
     * Terminado, pero con elementos fallidos.
     *
     * <p>Es un exito parcial deliberado: en una carga masiva un elemento invalido no debe tumbar
     * los otros mil. El detalle de los primeros fallos queda en {@code error_details_json}.</p>
     */
    COMPLETED_WITH_ERRORS,

    /** Error global que impidio continuar. El motivo queda en {@code error_message}. */
    FAILED,

    /**
     * Rechazado por falta de capacidad: no habia permiso libre para esta clase de trabajo.
     *
     * <p>Se persiste en lugar de descartarse en silencio para que el rechazo sea observable: quien
     * lanzo la peticion tiene un identificador que consultar y explotacion puede contar cuantas
     * veces se llego al tope sin depender de los logs.</p>
     */
    REJECTED;

    /** {@code true} si el trabajo ya no va a cambiar de estado. */
    public boolean isTerminal() {
        return this != PENDING && this != RUNNING;
    }
}
