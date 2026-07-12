package com.alejandro.mtoconfiguration.controller.asynchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.SteadyArmFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.asynchronous.SteadyArmAsyncService;
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
@RequestMapping(value = "/api/v1/async/steady-arm")
@Tag(
        name = "Steady Arms Async",
        description = "Asynchronous operations for steady arm management"
)
@ApiResponsesStandard
public class SteadyArmAsyncController {

    private final SteadyArmAsyncService steadyArmAsyncService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Get steady arm by ID (async)",
            description = "Asynchronously retrieves a steady arm by its identifier."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SteadyArmDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getByIdAsync(@PathVariable Long id) {
        return steadyArmAsyncService.getByIdAsync(id)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping
    @Operation(
            summary = "List all steady arms (async)",
            description = "Asynchronously retrieves all steady arms."
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync() {
        return steadyArmAsyncService.findAllAsync()
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(
            summary = "Create steady arm (async)",
            description = "Asynchronously creates a new steady arm."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SteadyArmDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> createAsync(@RequestBody SteadyArmDTO dto) {
        return steadyArmAsyncService.createAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create steady arms (async)",
            description = "Asynchronously creates several steady arms."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkCreateAsync(@RequestBody List<SteadyArmDTO> dtoList) {
        return steadyArmAsyncService.bulkCreateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update steady arm (async)",
            description = "Asynchronously updates an existing steady arm."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SteadyArmDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> updateAsync(
            @PathVariable Long id,
            @RequestBody SteadyArmDTO dto
    ) {
        dto.setId(id);
        return steadyArmAsyncService.updateAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update steady arms (async)",
            description = "Asynchronously updates several steady arms."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkUpdateAsync(@RequestBody List<SteadyArmDTO> dtoList) {
        return steadyArmAsyncService.bulkUpdateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search steady arms (async)",
            description = "Asynchronously searches for steady arms applying filters and pagination."
    )
    public CompletableFuture<ResponseEntity<Object>> searchAsync(@RequestBody SearchRequestDTO searchRequestDTO) {
        return steadyArmAsyncService.searchAsync(searchRequestDTO)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter steady arms (async)",
            description = "Asynchronously retrieves a page of steady arms applying specific filters."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SteadyArmDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getSteadyArmsAsync(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestBody SteadyArmFilter filter
    ) {
        return steadyArmAsyncService.getSteadyArmsAsync(pageable, filter)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/cantilever/{cantileverId}")
    @Operation(
            summary = "Get steady arm by Cantilever ID (async)",
            description = "Asynchronously retrieves the steady arm associated with a specific cantilever."
    )
    public CompletableFuture<ResponseEntity<Object>> getByCantileverIdAsync(@PathVariable Long cantileverId) {
        return steadyArmAsyncService.getByCantileverIdAsync(cantileverId)
                .thenApply(res -> res.<ResponseEntity<Object>>map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build()));
    }
}
