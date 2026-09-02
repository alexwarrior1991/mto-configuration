package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;

/**
 * Resultado de encolar una importacion de LOVs.
 *
 * <p>Distingue el 202 del 429: si no habia cupo, el trabajo existe igualmente con estado
 * REJECTED —queda constancia de que se pidio y de por que no se hizo— pero no se ha
 * encolado nada.
 */
public record LovImportJobSubmission(AsyncJob job, boolean accepted) {

    public static LovImportJobSubmission accepted(AsyncJob job) {
        return new LovImportJobSubmission(job, true);
    }

    public static LovImportJobSubmission rejected(AsyncJob job) {
        return new LovImportJobSubmission(job, false);
    }
}
