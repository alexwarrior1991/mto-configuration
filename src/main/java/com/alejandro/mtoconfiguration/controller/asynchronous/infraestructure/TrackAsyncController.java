package com.alejandro.mtoconfiguration.controller.asynchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.TrackFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.asynchronous.TrackAsyncService;
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
@RequestMapping(value = "/api/v1/async/track")
@Tag(
        name = "Tracks Async",
        description = "Asynchronous operations for track management"
)
public class TrackAsyncController {

    private final TrackAsyncService trackAsyncService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Get track by ID (async)",
            description = "Asynchronously retrieves a track by its identifier."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getByIdAsync(@PathVariable Long id) {
        return trackAsyncService.getByIdAsync(id)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping
    @Operation(
            summary = "List all tracks (async)",
            description = "Asynchronously retrieves all tracks."
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync() {
        return trackAsyncService.findAllAsync()
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(
            summary = "Create track (async)",
            description = "Asynchronously creates a new track."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> createAsync(@RequestBody TrackDTO dto) {
        return trackAsyncService.createAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create tracks (async)",
            description = "Asynchronously creates several tracks."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkCreateAsync(@RequestBody List<TrackDTO> dtoList) {
        return trackAsyncService.bulkCreateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update track (async)",
            description = "Asynchronously updates an existing track."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> updateAsync(
            @PathVariable Long id,
            @RequestBody TrackDTO dto
    ) {
        dto.setId(id);
        return trackAsyncService.updateAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update tracks (async)",
            description = "Asynchronously updates several tracks."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkUpdateAsync(@RequestBody List<TrackDTO> dtoList) {
        return trackAsyncService.bulkUpdateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search tracks (async)",
            description = "Asynchronously searches for tracks applying filters and pagination."
    )
    public CompletableFuture<ResponseEntity<Object>> searchAsync(@RequestBody SearchRequestDTO searchRequestDTO) {
        return trackAsyncService.searchAsync(searchRequestDTO)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter tracks (async)",
            description = "Asynchronously retrieves a page of tracks applying specific filters."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getTracksAsync(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestBody TrackFilter filter
    ) {
        return trackAsyncService.getTracksAsync(pageable, filter)
                .thenApply(ResponseEntity::ok);
    }
}
