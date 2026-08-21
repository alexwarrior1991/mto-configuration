package com.alejandro.mtoconfiguration.controller.asynchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.StationFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.asynchronous.StationAsyncService;
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
@RequestMapping(ConfigurationApiPaths.ASYNC_BASE_PATH + "/stations")
@Tag(
        name = "Stations Async",
        description = "Asynchronous operations for station management"
)
@ApiResponsesStandard
public class StationAsyncController {

    private final StationAsyncService stationAsyncService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Get station by ID (async)",
            description = "Asynchronously retrieves a station by its identifier."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = StationDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getByIdAsync(@PathVariable Long id) {
        return stationAsyncService.getByIdAsync(id)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping
    @Operation(
            summary = "List all stations (async)",
            description = "Asynchronously retrieves all stations."
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync() {
        return stationAsyncService.findAllAsync()
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(
            summary = "Create station (async)",
            description = "Asynchronously creates a new station."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = StationDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> createAsync(@Valid @RequestBody StationDTO dto) {
        return stationAsyncService.createAsync(dto)
                .thenApply(result -> ResponseEntity.status(HttpStatus.CREATED).body((Object) result));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create stations (async)",
            description = "Asynchronously creates several stations."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkCreateAsync(@Valid @RequestBody List<@Valid StationDTO> dtoList) {
        return stationAsyncService.bulkCreateAsync(dtoList)
                .thenApply(result -> ResponseEntity.status(HttpStatus.CREATED).body((Object) result));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update station (async)",
            description = "Asynchronously updates an existing station."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = StationDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> updateAsync(
            @PathVariable Long id,
            @Valid @RequestBody StationDTO dto
    ) {
        dto.setId(id);
        return stationAsyncService.updateAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update stations (async)",
            description = "Asynchronously updates several stations."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkUpdateAsync(@Valid @RequestBody List<@Valid StationDTO> dtoList) {
        return stationAsyncService.bulkUpdateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/paged")
    @Operation(
            summary = "List all stations paginated (async)",
            description = "Asynchronously retrieves a page of stations applying the requested pagination and sorting."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = StationDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync(@PageableDefault(size = 20) Pageable pageable) {
        return stationAsyncService.findAllAsync(pageable)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search stations (async)",
            description = "Asynchronously searches for stations applying filters and pagination."
    )
    public CompletableFuture<ResponseEntity<Object>> searchAsync(@Valid @RequestBody SearchRequestDTO searchRequestDTO) {
        return stationAsyncService.searchAsync(searchRequestDTO)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter stations (async)",
            description = "Asynchronously retrieves a page of stations applying specific filters."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = StationDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getStationsAsync(
            @PageableDefault(size = 20) Pageable pageable,
            @Valid @RequestBody StationFilter filter
    ) {
        return stationAsyncService.getStationsAsync(pageable, filter)
                .thenApply(ResponseEntity::ok);
    }


}
