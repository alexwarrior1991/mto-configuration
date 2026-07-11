package com.alejandro.mtoconfiguration.controller.asynchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.DisconnectorFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.asynchronous.DisconnectorAsyncService;
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
@RequestMapping(value = "/api/v1/async/disconnector")
@Tag(
        name = "Disconnectors Async",
        description = "Asynchronous operations for disconnector management"
)
public class DisconnectorAsyncController {

    private final DisconnectorAsyncService disconnectorAsyncService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Get disconnector by ID (async)",
            description = "Asynchronously retrieves a disconnector by its identifier."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = DisconnectorDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getByIdAsync(@PathVariable Long id) {
        return disconnectorAsyncService.getByIdAsync(id)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping
    @Operation(
            summary = "List all disconnectors (async)",
            description = "Asynchronously retrieves all disconnectors."
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync() {
        return disconnectorAsyncService.findAllAsync()
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(
            summary = "Create disconnector (async)",
            description = "Asynchronously creates a new disconnector."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = DisconnectorDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> createAsync(@RequestBody DisconnectorDTO dto) {
        return disconnectorAsyncService.createAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create disconnectors (async)",
            description = "Asynchronously creates several disconnectors."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkCreateAsync(@RequestBody List<DisconnectorDTO> dtoList) {
        return disconnectorAsyncService.bulkCreateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update disconnector (async)",
            description = "Asynchronously updates an existing disconnector."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = DisconnectorDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> updateAsync(
            @PathVariable Long id,
            @RequestBody DisconnectorDTO dto
    ) {
        dto.setId(id);
        return disconnectorAsyncService.updateAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update disconnectors (async)",
            description = "Asynchronously updates several disconnectors."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkUpdateAsync(@RequestBody List<DisconnectorDTO> dtoList) {
        return disconnectorAsyncService.bulkUpdateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search disconnectors (async)",
            description = "Asynchronously searches for disconnectors applying filters and pagination."
    )
    public CompletableFuture<ResponseEntity<Object>> searchAsync(@RequestBody SearchRequestDTO searchRequestDTO) {
        return disconnectorAsyncService.searchAsync(searchRequestDTO)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter disconnectors (async)",
            description = "Asynchronously retrieves a page of disconnectors applying specific filters."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = DisconnectorDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getDisconnectorsAsync(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestBody DisconnectorFilter filter
    ) {
        return disconnectorAsyncService.getDisconnectorsAsync(pageable, filter)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/station/{stationId}")
    @Operation(
            summary = "Get disconnectors by station ID (async)",
            description = "Asynchronously retrieves a list of disconnectors associated with a specific station."
    )
    public CompletableFuture<ResponseEntity<Object>> getByStationIdAsync(@PathVariable Long stationId) {
        return disconnectorAsyncService.getDisconnectorByStationIdAsync(stationId)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/station/name/{stationName}")
    @Operation(
            summary = "Get disconnectors by station name (async)",
            description = "Asynchronously retrieves a list of disconnectors whose station name matches the provided one."
    )
    public CompletableFuture<ResponseEntity<Object>> getByStationNameAsync(@PathVariable String stationName) {
        return disconnectorAsyncService.getDisconnectorsByStationNameAsync(stationName)
                .thenApply(ResponseEntity::ok);
    }
}
