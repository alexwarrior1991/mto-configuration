package com.alejandro.mtoconfiguration.controller.asynchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.SectionInsulatorFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.asynchronous.SectionInsulatorAsyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/async/section-insulator")
@Tag(
        name = "Section Insulators Async",
        description = "Asynchronous operations for section insulator management"
)
@ApiResponsesStandard
public class SectionInsulatorAsyncController {

    private final SectionInsulatorAsyncService sectionInsulatorAsyncService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Get section insulator by ID (async)",
            description = "Asynchronously retrieves a section insulator by its identifier."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SectionInsulatorDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getByIdAsync(@PathVariable Long id) {
        return sectionInsulatorAsyncService.getByIdAsync(id)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping
    @Operation(
            summary = "List all section insulators (async)",
            description = "Asynchronously retrieves all section insulators."
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync() {
        return sectionInsulatorAsyncService.findAllAsync()
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(
            summary = "Create section insulator (async)",
            description = "Asynchronously creates a new section insulator."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SectionInsulatorDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> createAsync(@RequestBody SectionInsulatorDTO dto) {
        return sectionInsulatorAsyncService.createAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create section insulators (async)",
            description = "Asynchronously creates several section insulators."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkCreateAsync(@RequestBody List<SectionInsulatorDTO> dtoList) {
        return sectionInsulatorAsyncService.bulkCreateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update section insulator (async)",
            description = "Asynchronously updates an existing section insulator."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SectionInsulatorDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> updateAsync(
            @PathVariable Long id,
            @RequestBody SectionInsulatorDTO dto
    ) {
        dto.setId(id);
        return sectionInsulatorAsyncService.updateAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update section insulators (async)",
            description = "Asynchronously updates several section insulators."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkUpdateAsync(@RequestBody List<SectionInsulatorDTO> dtoList) {
        return sectionInsulatorAsyncService.bulkUpdateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search section insulators (async)",
            description = "Asynchronously searches for section insulators applying filters and pagination."
    )
    public CompletableFuture<ResponseEntity<Object>> searchAsync(@RequestBody SearchRequestDTO searchRequestDTO) {
        return sectionInsulatorAsyncService.searchAsync(searchRequestDTO)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter section insulators (async)",
            description = "Asynchronously retrieves a page of section insulators applying specific filters."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SectionInsulatorDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getSectionInsulatorsAsync(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestBody SectionInsulatorFilter filter
    ) {
        return sectionInsulatorAsyncService.getSectionInsulatorsAsync(pageable, filter)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/station/{stationId}")
    @Operation(
            summary = "Get section insulators by station ID (async)",
            description = "Asynchronously retrieves section insulators associated with a station."
    )
    public CompletableFuture<ResponseEntity<Object>> getByStationIdAsync(@PathVariable Long stationId) {
        return sectionInsulatorAsyncService.getSectionInsulatorsByStationIdAsync(stationId)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/station/name/{stationName}")
    @Operation(
            summary = "Get section insulators by station name (async)",
            description = "Asynchronously retrieves section insulators by station name."
    )
    public CompletableFuture<ResponseEntity<Object>> getByStationNameAsync(@PathVariable String stationName) {
        return sectionInsulatorAsyncService.getSectionInsulatorsByStationNameAsync(stationName)
                .thenApply(ResponseEntity::ok);
    }
}
