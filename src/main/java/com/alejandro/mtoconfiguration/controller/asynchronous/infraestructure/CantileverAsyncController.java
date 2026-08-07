package com.alejandro.mtoconfiguration.controller.asynchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.CantileverFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.asynchronous.CantileverAsyncService;
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
@RequestMapping(ConfigurationApiPaths.ASYNC_BASE_PATH + "/cantilevers")
@Tag(
        name = "Cantilevers Async",
        description = "Asynchronous operations for cantilever management"
)
@ApiResponsesStandard
public class CantileverAsyncController {

    private final CantileverAsyncService cantileverAsyncService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Get cantilever by ID (async)",
            description = "Asynchronously retrieves a cantilever by its identifier."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = CantileverDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getByIdAsync(@PathVariable Long id) {
        return cantileverAsyncService.getByIdAsync(id)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping
    @Operation(
            summary = "List all cantilevers (async)",
            description = "Asynchronously retrieves all cantilevers."
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync() {
        return cantileverAsyncService.findAllAsync()
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(
            summary = "Create cantilever (async)",
            description = "Asynchronously creates a new cantilever."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = CantileverDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> createAsync(@Valid @RequestBody CantileverDTO dto) {
        return cantileverAsyncService.createAsync(dto)
                .thenApply(result -> ResponseEntity.status(HttpStatus.CREATED).body((Object) result));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create cantilevers (async)",
            description = "Asynchronously creates several cantilevers."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkCreateAsync(@Valid @RequestBody List<@Valid CantileverDTO> dtoList) {
        return cantileverAsyncService.bulkCreateAsync(dtoList)
                .thenApply(result -> ResponseEntity.status(HttpStatus.CREATED).body((Object) result));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update cantilever (async)",
            description = "Asynchronously updates an existing cantilever."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = CantileverDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> updateAsync(
            @PathVariable Long id,
            @Valid @RequestBody CantileverDTO dto
    ) {
        dto.setId(id);
        return cantileverAsyncService.updateAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update cantilevers (async)",
            description = "Asynchronously updates several cantilevers."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkUpdateAsync(@Valid @RequestBody List<@Valid CantileverDTO> dtoList) {
        return cantileverAsyncService.bulkUpdateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search cantilevers (async)",
            description = "Asynchronously searches for cantilevers applying filters and pagination."
    )
    public CompletableFuture<ResponseEntity<Object>> searchAsync(@Valid @RequestBody SearchRequestDTO searchRequestDTO) {
        return cantileverAsyncService.searchAsync(searchRequestDTO)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter cantilevers (async)",
            description = "Asynchronously retrieves a page of cantilevers applying specific filters."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = CantileverDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getCantileversAsync(
            @PageableDefault(size = 20) Pageable pageable,
            @Valid @RequestBody CantileverFilter filter
    ) {
        return cantileverAsyncService.getCantileversAsync(pageable, filter)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/profile/{profileId}")
    @Operation(
            summary = "Get cantilevers by Profile ID (async)",
            description = "Asynchronously retrieves all cantilevers associated with a specific profile."
    )
    public CompletableFuture<ResponseEntity<Object>> getByProfileIdAsync(@PathVariable Long profileId) {
        return cantileverAsyncService.getByProfileIdAsync(profileId)
                .thenApply(ResponseEntity::ok);
    }
}
