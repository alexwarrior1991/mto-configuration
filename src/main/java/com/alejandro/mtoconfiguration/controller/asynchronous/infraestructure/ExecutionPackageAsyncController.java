package com.alejandro.mtoconfiguration.controller.asynchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ExecutionPackageFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.asynchronous.ExecutionPackageAsyncService;
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
@RequestMapping(ConfigurationApiPaths.ASYNC_BASE_PATH + "/execution-packages")
@Tag(
        name = "Execution Packages Async",
        description = "Asynchronous operations for execution package management"
)
@ApiResponsesStandard
public class ExecutionPackageAsyncController {

    private final ExecutionPackageAsyncService executionPackageAsyncService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Get execution package by ID (async)",
            description = "Asynchronously retrieves an execution package by its identifier, running on the configured executor."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ExecutionPackageDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public CompletableFuture<ResponseEntity<Object>> getByIdAsync(@PathVariable Long id) {
        return executionPackageAsyncService.getByIdAsync(id)
                .thenApply(ResponseEntity::ok);
    }


    @GetMapping
    @Operation(
            summary = "List all execution packages (async)",
            description = "Asynchronously retrieves all execution packages."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    public CompletableFuture<ResponseEntity<Object>> findAllAsync() {
        return executionPackageAsyncService.findAllAsync()
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(
            summary = "Create execution package (async)",
            description = "Asynchronously creates a new execution package. Executes validations, business logic, persistence, and cache cleanup."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ExecutionPackageDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public CompletableFuture<ResponseEntity<Object>> createAsync(@Valid @RequestBody ExecutionPackageDTO dto) {
        return executionPackageAsyncService.createAsync(dto)
                .thenApply(result -> ResponseEntity.status(HttpStatus.CREATED).body((Object) result));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create execution packages (async)",
            description = "Asynchronously creates several execution packages in a single transactional operation."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public CompletableFuture<ResponseEntity<Object>> bulkCreateAsync(@Valid @RequestBody List<@Valid ExecutionPackageDTO> dtoList) {
        return executionPackageAsyncService.bulkCreateAsync(dtoList)
                .thenApply(result -> ResponseEntity.status(HttpStatus.CREATED).body((Object) result));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update execution package (async)",
            description = "Asynchronously updates an existing package. The path ID is assigned to the DTO before calling the service."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ExecutionPackageDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public CompletableFuture<ResponseEntity<Object>> updateAsync(
            @PathVariable Long id,
            @Valid @RequestBody ExecutionPackageDTO dto
    ) {
        dto.setId(id);
        return executionPackageAsyncService.updateAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update execution packages (async)",
            description = "Asynchronously updates several packages in a single transaction. If one fails, all are rolled back."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public CompletableFuture<ResponseEntity<Object>> bulkUpdateAsync(@Valid @RequestBody List<@Valid ExecutionPackageDTO> dtoList) {
        return executionPackageAsyncService.bulkUpdateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/paged")
    @Operation(
            summary = "List all execution packages paginated (async)",
            description = "Asynchronously retrieves a page of execution packages applying the requested pagination and sorting."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ExecutionPackageDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync(@PageableDefault(size = 20) Pageable pageable) {
        return executionPackageAsyncService.findAllAsync(pageable)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search execution packages (async)",
            description = "Asynchronously searches for execution packages applying filters, sorting, and pagination."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public CompletableFuture<ResponseEntity<Object>> searchAsync(@Valid @RequestBody SearchRequestDTO searchRequestDTO) {
        return executionPackageAsyncService.searchAsync(searchRequestDTO)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter execution packages (async)",
            description = "Asynchronously retrieves a page of execution packages applying specific filters using QueryDSL."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ExecutionPackageDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public CompletableFuture<ResponseEntity<Object>> getExecutionPackagesAsync(
            @PageableDefault(size = 20) Pageable pageable,
            @Valid @RequestBody ExecutionPackageFilter filter
    ) {
        return executionPackageAsyncService.getExecutionPackagesAsync(pageable, filter)
                .thenApply(ResponseEntity::ok);
    }
}
