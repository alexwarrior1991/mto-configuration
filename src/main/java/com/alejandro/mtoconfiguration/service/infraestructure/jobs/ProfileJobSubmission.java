package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;

/**
 * Resultado de pedir un trabajo: la fila creada y si llego a encolarse.
 *
 * <p>Existe para que el controlador pueda distinguir el 202 del 429 sin volver a interpretar el
 * estado del trabajo. La decision la toma el servicio, que es quien sabe si habia hueco.</p>
 *
 * @param accepted {@code false} cuando se rechazo por falta de capacidad; el trabajo existe igual,
 *                 en estado REJECTED
 */
public record ProfileJobSubmission(AsyncJob job, boolean accepted) {

    static ProfileJobSubmission accepted(AsyncJob job) {
        return new ProfileJobSubmission(job, true);
    }

    static ProfileJobSubmission rejected(AsyncJob job) {
        return new ProfileJobSubmission(job, false);
    }
}
