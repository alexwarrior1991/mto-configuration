package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;

/**
 * Resultado de pedir un republicado: la fila creada y si llego a encolarse.
 *
 * <p>Tipo propio y no {@code ProfileJobSubmission} por lo mismo que la importacion tiene el suyo:
 * lo encola otro servicio. La respuesta HTTP si es la misma —mismo 202, mismo 429 y mismo
 * {@code Location}.</p>
 *
 * @param accepted {@code false} cuando se rechazo por falta de capacidad; el trabajo existe igual,
 *                 en estado REJECTED
 */
public record MasterDataRepublishJobSubmission(AsyncJob job, boolean accepted) {

    static MasterDataRepublishJobSubmission accepted(AsyncJob job) {
        return new MasterDataRepublishJobSubmission(job, true);
    }

    static MasterDataRepublishJobSubmission rejected(AsyncJob job) {
        return new MasterDataRepublishJobSubmission(job, false);
    }
}
