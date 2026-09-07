package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;

/**
 * Resultado de encolar una importacion del maestro de perfiles.
 *
 * <p>Distingue el 202 del 429: si no habia cupo, el trabajo existe igualmente con estado
 * REJECTED —queda constancia de que se pidio y de por que no se hizo— pero no se ha
 * encolado nada.
 */
public record ProfileImportJobSubmission(AsyncJob job, boolean accepted) {

    public static ProfileImportJobSubmission accepted(AsyncJob job) {
        return new ProfileImportJobSubmission(job, true);
    }

    public static ProfileImportJobSubmission rejected(AsyncJob job) {
        return new ProfileImportJobSubmission(job, false);
    }
}
