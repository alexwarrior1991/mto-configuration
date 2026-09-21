package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.AsyncJobListItem;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.AsyncJobQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El listado de trabajos en segundo plano de todas las familias. Solo lectura: lanzar, consultar
 * el detalle y descargar siguen en cada familia ({@code /profiles/jobs}, {@code /lovs/jobs},
 * {@code /master-data/republish}).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/jobs")
@Tag(name = "Jobs", description = "Background jobs of every family, newest first")
public class AsyncJobController {

    private final AsyncJobQueryService jobs;

    @GetMapping
    @Operation(
            summary = "List background jobs",
            description = """
                    Every job of every family, newest first, optionally filtered by type and status.
                    Paged with the usual page, size and sort parameters (sort by createdAt, startedAt,
                    finishedAt, status or type; anything else falls back to createdAt,desc)."""
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = AsyncJobListItem.class))
    )
    public ResponseEntity<Page<AsyncJobListItem>> list(
            @RequestParam(required = false) JobType type,
            @RequestParam(required = false) JobStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(jobs.list(type, status, pageable));
    }
}
