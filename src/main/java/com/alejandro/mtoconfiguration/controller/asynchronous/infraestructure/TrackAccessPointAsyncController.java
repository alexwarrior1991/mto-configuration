package com.alejandro.mtoconfiguration.controller.asynchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackAccessPointDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.TrackAccessPointFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.asynchronous.TrackAccessPointAsyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.ASYNC_BASE_PATH + "/track-access-points")
@Tag(
        name = "Track Access Points Async",
        description = "Asynchronous operations for track access point management"
)
@ApiResponsesStandard
public class TrackAccessPointAsyncController {

    private final TrackAccessPointAsyncService trackAccessPointAsyncService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Get track access point by ID (async)",
            description = "Asynchronously retrieves a track access point by its identifier."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackAccessPointDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getByIdAsync(@PathVariable Long id) {
        return trackAccessPointAsyncService.getByIdAsync(id)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping
    @Operation(
            summary = "List all track access points (async)",
            description = "Asynchronously retrieves all track access points."
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync() {
        return trackAccessPointAsyncService.findAllAsync()
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(
            summary = "Create track access point (async)",
            description = "Asynchronously creates a new track access point."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackAccessPointDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> createAsync(@Valid @RequestBody TrackAccessPointDTO dto) {
        return trackAccessPointAsyncService.createAsync(dto)
                .thenApply(result -> ResponseEntity.status(HttpStatus.CREATED).body((Object) result));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create track access points (async)",
            description = "Asynchronously creates several track access points."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkCreateAsync(
            @Valid @RequestBody List<@Valid TrackAccessPointDTO> dtoList) {
        return trackAccessPointAsyncService.bulkCreateAsync(dtoList)
                .thenApply(result -> ResponseEntity.status(HttpStatus.CREATED).body((Object) result));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update track access point (async)",
            description = "Asynchronously updates an existing track access point."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackAccessPointDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> updateAsync(
            @PathVariable Long id,
            @Valid @RequestBody TrackAccessPointDTO dto
    ) {
        dto.setId(id);
        return trackAccessPointAsyncService.updateAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update track access points (async)",
            description = "Asynchronously updates several track access points."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkUpdateAsync(
            @Valid @RequestBody List<@Valid TrackAccessPointDTO> dtoList) {
        return trackAccessPointAsyncService.bulkUpdateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/paged")
    @Operation(
            summary = "List all track access points paginated (async)",
            description = "Asynchronously retrieves a page of track access points applying the requested "
                    + "pagination and sorting."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackAccessPointDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync(@PageableDefault(size = 20) Pageable pageable) {
        return trackAccessPointAsyncService.findAllAsync(pageable)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search track access points (async)",
            description = "Asynchronously searches for track access points applying filters and pagination."
    )
    public CompletableFuture<ResponseEntity<Object>> searchAsync(@Valid @RequestBody SearchRequestDTO searchRequestDTO) {
        return trackAccessPointAsyncService.searchAsync(searchRequestDTO)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter track access points (async)",
            description = "Asynchronously retrieves a page of track access points applying specific filters."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackAccessPointDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getTrackAccessPointsAsync(
            @PageableDefault(size = 20) Pageable pageable,
            @Valid @RequestBody TrackAccessPointFilter filter
    ) {
        return trackAccessPointAsyncService.getTrackAccessPointsAsync(pageable, filter)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/track/{trackId}")
    @Operation(
            summary = "Get track access points by track ID (async)",
            description = "Asynchronously retrieves the track access points of a track."
    )
    public CompletableFuture<ResponseEntity<Object>> getByTrackIdAsync(@PathVariable Long trackId) {
        return trackAccessPointAsyncService.getTrackAccessPointsByTrackIdAsync(trackId)
                .thenApply(ResponseEntity::ok);
    }
}
