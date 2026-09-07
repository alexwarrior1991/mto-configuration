package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.ProfileJobResponse;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.ProfileImportJobService;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.ProfileImportJobSubmission;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.ProfileJobFiles;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.ProfileJobResponseMapper;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.ProfileJobService;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.ProfileJobSubmission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Operaciones de perfiles que se ejecutan de verdad en segundo plano.
 *
 * <h2>Que las diferencia de {@code /async/profiles}</h2>
 *
 * <p>Ningun metodo de aqui devuelve {@code CompletableFuture}. Los de {@code /async} si, y eso
 * significa que la peticion HTTP <b>sigue viva</b> hasta que el trabajo acaba: cambia el hilo que
 * lo hace, no quien espera. Aqui la respuesta sale en cuanto el trabajo esta encolado —202, un
 * identificador y un {@code Location}— y el trabajo continua aunque el cliente se desconecte al
 * instante siguiente.</p>
 *
 * <p>La consecuencia practica es que estos endpoints responden en milisegundos con independencia de
 * si detras hay diez elementos o cien mil, y que el resultado se recoge cuando convenga en vez de
 * mantener una conexion abierta durante minutos.</p>
 *
 * <p>Los endpoints antiguos siguen donde estaban y hacen lo mismo que hacian: esto es una capa
 * nueva en paralelo, no un reemplazo.</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ProfileJobResponseMapper.JOBS_PATH)
@Tag(
        name = "Profile Jobs",
        description = "Long-running profile operations: 202 Accepted, job id and polling"
)
@ApiResponsesStandard
public class ProfileJobController {

    private static final String CODE_202 = "202";
    private static final String CODE_409 = "409";
    private static final String CODE_410 = "410";
    private static final String CODE_429 = "429";

    private final ProfileJobService profileJobService;
    private final ProfileImportJobService profileImportJobService;
    private final ProfileJobResponseMapper responseMapper;
    private final ProfileJobFiles profileJobFiles;

    @PostMapping("/export")
    @Operation(
            summary = "Start a profile CSV export job",
            description = """
                    Queues a CSV export of every profile of a track and returns immediately.
                    The response carries the job id and a Location header pointing at the job status;
                    the file is downloadable through the job once it completes."""
    )
    @ApiResponse(
            responseCode = CODE_202,
            description = "Export job accepted and queued.",
            content = @Content(schema = @Schema(implementation = ProfileJobResponse.class))
    )
    @ApiResponse(responseCode = CODE_429, description = "No spare capacity: the job was rejected.")
    public ResponseEntity<ProfileJobResponse> startExport(
            @RequestParam Long trackId,
            @RequestParam(defaultValue = "basic") String mapperType
    ) {
        return respond(profileJobService.submitExport(trackId, mapperType));
    }

    @PostMapping("/bulk-create")
    @Operation(
            summary = "Start a bulk create job",
            description = """
                    Queues the creation of every profile of the list and returns immediately.
                    Items are processed one by one, each in its own transaction, so a single invalid
                    item does not undo the ones already created."""
    )
    @ApiResponse(
            responseCode = CODE_202,
            description = "Bulk create job accepted and queued.",
            content = @Content(schema = @Schema(implementation = ProfileJobResponse.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = CODE_429, description = "No spare capacity: the job was rejected.")
    public ResponseEntity<ProfileJobResponse> startBulkCreate(
            @Valid @RequestBody List<@Valid ProfileDTO> dtoList
    ) {
        return respond(profileJobService.submitBulkCreate(dtoList));
    }

    @PostMapping("/bulk-update")
    @Operation(
            summary = "Start a bulk update job",
            description = """
                    Queues the update of every profile of the list and returns immediately.
                    Every item must carry its id; the ones that do not are reported as failed items
                    without stopping the job."""
    )
    @ApiResponse(
            responseCode = CODE_202,
            description = "Bulk update job accepted and queued.",
            content = @Content(schema = @Schema(implementation = ProfileJobResponse.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = CODE_429, description = "No spare capacity: the job was rejected.")
    public ResponseEntity<ProfileJobResponse> startBulkUpdate(
            @Valid @RequestBody List<@Valid ProfileDTO> dtoList
    ) {
        return respond(profileJobService.submitBulkUpdate(dtoList));
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Start a profile master import job",
            description = """
                    Queues the load of profile-master.xlsx —execution packages, stations, tracks,
                    profiles and cantilevers— and returns immediately. Entities are upserted by their
                    natural key, so running the same master twice creates nothing the second time.
                    With dryRun=true nothing is written and the report is computed all the same."""
    )
    @ApiResponse(
            responseCode = CODE_202,
            description = "Import job accepted and queued.",
            content = @Content(schema = @Schema(implementation = ProfileJobResponse.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = CODE_429, description = "No spare capacity: the job was rejected.")
    public ResponseEntity<ProfileJobResponse> startImport(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun
    ) {
        // El contenido se lee AQUI, en el hilo de la peticion: el MultipartFile se borra en
        // cuanto la peticion termina, y el hilo de fondo arranca despues.
        return respond(profileImportJobService.submit(readContent(file), dryRun));
    }

    @GetMapping("/{jobId}")
    @Operation(
            summary = "Get job status",
            description = "Returns the current status and progress counters of a job."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ProfileJobResponse.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<ProfileJobResponse> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(responseMapper.toResponse(profileJobService.getJob(jobId)));
    }

    @GetMapping("/{jobId}/file")
    @Operation(
            summary = "Download the file produced by an export job",
            description = "Returns the generated CSV. Only available for completed export jobs."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = "CSV file.")
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = "No such job, or the job produces no file.")
    @ApiResponse(responseCode = CODE_409, description = "The job has not completed yet.")
    @ApiResponse(responseCode = CODE_410, description = "The job completed but the file is no longer available.")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID jobId) {
        // Un trabajo inexistente sale por NotFoundException y lo traduce el manejador global, de
        // modo que el 404 llega con el mismo cuerpo Problem Details que el resto de la API.
        AsyncJob job = profileJobService.getJob(jobId);

        boolean isImport = job.getType() == JobType.PROFILE_IMPORT;
        if (job.getType() != JobType.PROFILE_EXPORT && !isImport) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "El trabajo %s no produce ningun fichero".formatted(jobId));
        }

        // 409 y no 404: el recurso existe y la peticion es legitima, lo que pasa es que el estado
        // del trabajo todavia no permite servirlo. Un 404 mandaria al cliente a buscar un error en
        // su identificador cuando lo unico que le hace falta es esperar.
        //
        // La importacion admite ademas COMPLETED_WITH_ERRORS, y no por laxitud: su fichero es el
        // informe de lo que paso, asi que negarlo justo cuando hubo errores dejaria al cliente sin
        // lo unico que le dice QUE fila fallo y por que.
        if (!isDownloadable(job, isImport)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El trabajo %s esta en estado %s; la descarga aun no esta disponible"
                            .formatted(jobId, job.getStatus()));
        }

        // 410 y no 404: el fichero existio y ya no esta —purgado, o generado por otra replica que
        // no comparte disco—. La diferencia le importa al cliente, porque un 404 sugiere reintentar
        // y un 410 dice que hay que volver a lanzar el trabajo.
        Resource resource = (isImport ? importReport(job) : profileJobFiles.find(job))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE,
                        "El fichero del trabajo %s ya no esta disponible".formatted(jobId)));

        log.info("Descarga de fichero jobId={} type={} status={}", jobId, job.getType(), job.getStatus());

        MediaType contentType = isImport
                ? MediaType.APPLICATION_JSON
                : new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"%s\"".formatted(job.getFileName()))
                .body(resource);
    }

    /**
     * Una exportacion solo se sirve completa; una importacion, tambien cuando termino con
     * errores por fila: su fichero ES el informe de esos errores.
     */
    private boolean isDownloadable(AsyncJob job, boolean isImport) {
        return job.getStatus() == JobStatus.COMPLETED
                || (isImport && job.getStatus() == JobStatus.COMPLETED_WITH_ERRORS);
    }

    private java.util.Optional<Resource> importReport(AsyncJob job) {
        if (job.getFileName() == null || job.getFileName().isBlank()) {
            return java.util.Optional.empty();
        }
        Path path = profileImportJobService.reportPath(job.getFileName());
        return Files.isReadable(path)
                ? java.util.Optional.of(new FileSystemResource(path))
                : java.util.Optional.empty();
    }

    /**
     * El fichero se lee en el hilo de la peticion a proposito: el {@code MultipartFile} vive
     * lo que vive la peticion, y el hilo de fondo arranca despues. Leerlo alli daria un
     * temporal ya borrado.
     */
    private byte[] readContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El fichero del maestro de perfiles es obligatorio");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se ha podido leer el fichero subido", e);
        }
    }

    /**
     * 202 si el trabajo se encolo, 429 si no habia capacidad.
     *
     * <p>El {@code Location} viaja en los dos casos: tambien un rechazo tiene fila propia que
     * consultar, y darle la ruta evita que el cliente tenga que componerla a mano.</p>
     */
    private ResponseEntity<ProfileJobResponse> respond(ProfileJobSubmission submission) {
        return respond(submission.job(), submission.accepted());
    }

    /**
     * La importacion trae su propio tipo de resultado porque la encola otro servicio, pero la
     * respuesta HTTP es la misma: mismo 202, mismo 429 y mismo {@code Location}.
     */
    private ResponseEntity<ProfileJobResponse> respond(ProfileImportJobSubmission submission) {
        return respond(submission.job(), submission.accepted());
    }

    private ResponseEntity<ProfileJobResponse> respond(AsyncJob job, boolean accepted) {

        // ServletUriComponentsBuilder para que el Location salga con el esquema, el host y el
        // context-path reales de la peticion. Concatenar la ruta a mano devuelve un Location
        // relativo que se rompe en cuanto la aplicacion vive detras de un proxy o de un prefijo.
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(ProfileJobResponseMapper.statusPath(job.getId()))
                .build()
                .toUri();

        ProfileJobResponse body = responseMapper.toResponse(job);

        if (!accepted) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .location(location)
                    .header(HttpHeaders.RETRY_AFTER, "30")
                    .body(body);
        }

        return ResponseEntity.accepted()
                .location(location)
                .body(body);
    }
}
