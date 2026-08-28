package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs;

import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Estado de un trabajo en segundo plano.
 *
 * <p>Es el cuerpo del 202 que devuelve el arranque de un trabajo y el del GET que lo consulta: el
 * mismo documento en los dos sitios, para que el cliente no tenga que aprender dos formas.</p>
 *
 * <p>Los campos que no aplican se omiten ({@code NON_NULL}) en lugar de viajar a null: en un trabajo
 * recien creado eso deja una respuesta de cuatro campos en vez de una plantilla llena de huecos.</p>
 *
 * @param downloadUrl ruta HTTP de descarga, no una ruta del servidor, y solo cuando hay fichero que
 *                    descargar. La ruta local del CSV no sale nunca de la aplicacion: al cliente no
 *                    le sirve de nada y describe el sistema de ficheros a quien pregunte.
 * @param itemErrors  primeros errores por elemento, acotados por configuracion. El recuento
 *                    completo esta en {@code failedItems}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileJobResponse(
        UUID id,
        JobType type,
        JobStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Long trackId,
        String mapperType,
        Integer totalItems,
        int processedItems,
        int successfulItems,
        int failedItems,
        String downloadUrl,
        String error,
        List<JobItemErrorDTO> itemErrors
) {
}
