package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.synchronous.lov.imports.LovImportJobResponse;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.LovImportJobResponseMapper;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.LovImportJobService;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.LovImportJobSubmission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Importacion del catalogo maestro de LOVs.
 *
 * <p>Sigue el patron de {@code ProfileJobController}: la peticion responde 202 con el
 * identificador del trabajo y una cabecera {@code Location}, y la importacion sigue
 * aunque el cliente se desconecte. Nunca devuelve {@code CompletableFuture}.
 *
 * <p>El fichero se lee entero a memoria en el hilo de la peticion, a proposito: el
 * {@code MultipartFile} se destruye al terminar la peticion, y el hilo de fondo arranca
 * despues. El maestro ronda los 100 KB, asi que el coste es irrelevante.
 *
 * <p>Permisos: el filtro exige {@code CONFIG_IMPORT} —la ruta esta declarada en el array
 * {@code BULK} de {@code SecurityConfiguration}— y ademas se pide el rol
 * {@code LOV_MANAGE}, igual que en el resto de escrituras sobre LOVs.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(LovImportJobResponseMapper.JOBS_PATH)
@Tag(name = "LOV import jobs", description = "Bulk load of the LOV master catalogue")
public class LovImportJobController {

    private static final String CODE_202 = "202";
    private static final String CODE_409 = "409";
    private static final String CODE_410 = "410";
    private static final String CODE_429 = "429";

    private final LovImportJobService lovImportJobService;
    private final LovImportJobResponseMapper responseMapper;

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('LOV_MANAGE')")
    @Operation(
            summary = "Import the LOV master catalogue",
            description = """
                    Accepts the lov-master.xlsx workbook and loads every row flagged ENABLED=SI,
                    creating or updating each LOV by its code. Re-importing the same file is
                    idempotent. With dryRun=true nothing is written and the report describes what
                    the real run would do.
                    The response carries the job id and a Location header pointing at the job status.
                    """
    )
    @ApiResponse(
            responseCode = CODE_202,
            description = "Import accepted.",
            content = @Content(schema = @Schema(implementation = LovImportJobResponse.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = CODE_429, description = "No free slot to run the import.")
    public ResponseEntity<LovImportJobResponse> importCatalogue(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun) {

        byte[] content = readAll(file);

        log.info("Importacion de catalogo solicitada fichero={} bytes={} dryRun={}",
                file.getOriginalFilename(), content.length, dryRun);

        return respond(lovImportJobService.submit(content, dryRun));
    }

    @GetMapping("/{jobId}")
    @Operation(
            summary = "Get the status of an import job",
            description = "Returns the current status and progress counters of an import job."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = LovImportJobResponse.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<LovImportJobResponse> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(responseMapper.toResponse(lovImportJobService.getJob(jobId)));
    }

    @GetMapping("/{jobId}/file")
    @Operation(
            summary = "Download the import report",
            description = "Returns the JSON report of a finished import, including dry runs."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = "JSON report.")
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = "No such job, or it produces no report.")
    @ApiResponse(responseCode = CODE_409, description = "The job has not finished yet.")
    @ApiResponse(responseCode = CODE_410, description = "The job finished but the report is no longer available.")
    public ResponseEntity<Resource> downloadReport(@PathVariable UUID jobId) {
        AsyncJob job = lovImportJobService.getJob(jobId);

        if (job.getType() != JobType.LOV_IMPORT) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "El trabajo %s no es una importacion de catalogo".formatted(jobId));
        }

        // 409 y no 404: el recurso existe y la peticion es legitima; lo que pasa es que el
        // informe aun no esta escrito. Un 404 mandaria a buscar un error en el identificador.
        JobStatus status = job.getStatus();
        if (status == null || !status.isTerminal()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El trabajo %s esta en estado %s; el informe solo esta disponible al terminar"
                            .formatted(jobId, status));
        }

        // 410 y no 404: el informe existio y ya no esta —purgado, o escrito por otra replica
        // que no comparte disco—. Al cliente le importa la diferencia: un 404 sugiere
        // reintentar y un 410 dice que hay que volver a lanzar la importacion.
        Resource resource = findReport(job)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE,
                        "El informe del trabajo %s ya no esta disponible".formatted(jobId)));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"%s\"".formatted(job.getFileName()))
                .body(resource);
    }

    private java.util.Optional<Resource> findReport(AsyncJob job) {
        if (job.getFileName() == null) {
            return java.util.Optional.empty();
        }

        Path path = lovImportJobService.reportPath(job.getFileName());
        return Files.isReadable(path)
                ? java.util.Optional.of(new FileSystemResource(path))
                : java.util.Optional.empty();
    }

    private byte[] readAll(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El fichero del catalogo maestro es obligatorio");
        }

        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se ha podido leer el fichero subido: " + e.getMessage(), e);
        }
    }

    /**
     * 202 si se encolo, 429 si no habia cupo. El {@code Location} viaja en los dos casos:
     * tambien un rechazo tiene fila propia que consultar.
     */
    private ResponseEntity<LovImportJobResponse> respond(LovImportJobSubmission submission) {
        AsyncJob job = submission.job();

        // ServletUriComponentsBuilder para que el Location salga con el esquema, el host y el
        // context-path reales: concatenar la ruta a mano se rompe detras de un proxy.
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(LovImportJobResponseMapper.statusPath(job.getId()))
                .build()
                .toUri();

        LovImportJobResponse body = responseMapper.toResponse(job);

        if (!submission.accepted()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .location(location)
                    .header(HttpHeaders.RETRY_AFTER, "30")
                    .body(body);
        }

        return ResponseEntity.accepted().location(location).body(body);
    }
}
