package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.MasterDataRepublishJobResponse;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.MasterDataRepublishJobResponseMapper;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.MasterDataRepublishJobService;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.MasterDataRepublishJobSubmission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Republicado de datos maestros hacia los consumidores.
 *
 * <h2>Para que existe</h2>
 *
 * <p>Los eventos de datos maestros solo nacen cuando algo pasa por la capa de servicio, asi que un
 * consumidor que se conecta a un dominio ya poblado nace vacio: nadie va a volver a editar los
 * once mil perfiles que ya estaban. Esto recorre lo que hay y escribe por cada elemento el mismo
 * evento que habria escrito una edicion, como {@code UPDATED}.</p>
 *
 * <p>Es <b>reejecutable</b>: el consumidor absorbe el republicado por su upsert idempotente y su
 * marca de agua, asi que lanzarlo dos veces no duplica nada al otro lado.</p>
 *
 * <h2>Por que 202 y no una respuesta sincrona</h2>
 *
 * <p>Porque una via con miles de perfiles se pasa del {@code GATEWAY_READ_TIMEOUT} de 15s de
 * {@code mto-gateway}. La respuesta sale en cuanto el trabajo esta encolado —202, un identificador
 * y un {@code Location}— y el trabajo continua aunque el cliente se desconecte al instante
 * siguiente.</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(MasterDataRepublishJobResponseMapper.JOBS_PATH)
@Tag(
        name = "Master Data Republish",
        description = "One-off republish of existing master data: 202 Accepted, job id and polling"
)
@ApiResponsesStandard
public class MasterDataRepublishJobController {

    private static final String CODE_202 = "202";
    private static final String CODE_429 = "429";

    /** Segundos que se le piden al cliente antes de reintentar cuando no hay cupo. */
    private static final String RETRY_AFTER_SECONDS = "30";

    private final MasterDataRepublishJobService republishJobService;
    private final MasterDataRepublishJobResponseMapper responseMapper;

    @PostMapping
    @Operation(
            summary = "Start a master data republish job",
            description = """
                    Queues a republish of the master data that already exists and returns \
                    immediately. Every selected entity is re-read with its messaging entity graph \
                    and written to the outbox as an UPDATED event, exactly as a real edit would.

                    entity: profile | disconnector | section-insulator | all.
                    trackId narrows profiles only; stationId narrows disconnectors and section \
                    insulators only; all takes no filter.

                    Re-running it is harmless: consumers absorb it through their idempotent upsert \
                    and their sequence watermark."""
    )
    @ApiResponse(
            responseCode = CODE_202,
            description = "Republish job accepted and queued.",
            content = @Content(schema = @Schema(implementation = MasterDataRepublishJobResponse.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = CODE_429, description = "No spare capacity: the job was rejected.")
    public ResponseEntity<MasterDataRepublishJobResponse> startRepublish(
            // Sin valor por defecto a proposito: omitir el parametro no puede significar
            // «republicalo todo». Una ausencia sale como 400 con los valores admitidos.
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) Long trackId,
            @RequestParam(required = false) Long stationId
    ) {
        return respond(republishJobService.submit(entity, trackId, stationId));
    }

    @GetMapping("/{jobId}")
    @Operation(
            summary = "Get republish job status",
            description = "Returns the current status and progress counters of a republish job."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = MasterDataRepublishJobResponse.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<MasterDataRepublishJobResponse> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(responseMapper.toResponse(republishJobService.getJob(jobId)));
    }

    private ResponseEntity<MasterDataRepublishJobResponse> respond(MasterDataRepublishJobSubmission submission) {
        AsyncJob job = submission.job();

        // ServletUriComponentsBuilder para que el Location salga con el esquema, el host y el
        // context-path reales de la peticion. Concatenar la ruta a mano devuelve un Location
        // relativo que se rompe en cuanto la aplicacion vive detras de un proxy o de un prefijo.
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(MasterDataRepublishJobResponseMapper.statusPath(job.getId()))
                .build()
                .toUri();

        MasterDataRepublishJobResponse body = responseMapper.toResponse(job);

        // 429 Y fila REJECTED: el 429 con Retry-After es lo que un cliente entiende y sabe
        // reintentar —un 202 con un trabajo que nunca va a correr seria mentirle—, y la fila es lo
        // que hace el rechazo observable, con un identificador que consultar.
        if (!submission.accepted()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .location(location)
                    .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                    .body(body);
        }

        return ResponseEntity.accepted()
                .location(location)
                .body(body);
    }
}
