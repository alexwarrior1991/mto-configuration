package com.alejandro.mtoconfiguration.controller.asynchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ProfileFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.asynchronous.ProfileAsyncService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.ASYNC_BASE_PATH + "/profiles")
@Tag(
        name = "Profiles Async",
        description = "Asynchronous operations for profile (poles) management"
)
@ApiResponsesStandard
public class ProfileAsyncController {

    private final ProfileAsyncService profileAsyncService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Get profile by ID (async)",
            description = "Asynchronously retrieves a profile by its identifier."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ProfileDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getByIdAsync(@PathVariable Long id) {
        return profileAsyncService.getByIdAsync(id)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping
    @Operation(
            summary = "List all profiles (async)",
            description = "Asynchronously retrieves all profiles."
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync() {
        return profileAsyncService.findAllAsync()
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(
            summary = "Create profile (async)",
            description = "Asynchronously creates a new profile."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ProfileDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> createAsync(@Valid @RequestBody ProfileDTO dto) {
        return profileAsyncService.createAsync(dto)
                .thenApply(result -> ResponseEntity.status(HttpStatus.CREATED).body((Object) result));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create profiles (async)",
            description = "Asynchronously creates several profiles."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkCreateAsync(@Valid @RequestBody List<@Valid ProfileDTO> dtoList) {
        return profileAsyncService.bulkCreateAsync(dtoList)
                .thenApply(result -> ResponseEntity.status(HttpStatus.CREATED).body((Object) result));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update profile (async)",
            description = "Asynchronously updates an existing profile."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ProfileDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> updateAsync(
            @PathVariable Long id,
            @Valid @RequestBody ProfileDTO dto
    ) {
        dto.setId(id);
        return profileAsyncService.updateAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update profiles (async)",
            description = "Asynchronously updates several profiles."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkUpdateAsync(@Valid @RequestBody List<@Valid ProfileDTO> dtoList) {
        return profileAsyncService.bulkUpdateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/paged")
    @Operation(
            summary = "List all profiles paginated (async)",
            description = "Asynchronously retrieves a page of profiles applying the requested pagination and sorting."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ProfileDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync(@PageableDefault(size = 20) Pageable pageable) {
        return profileAsyncService.findAllAsync(pageable)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search profiles (async)",
            description = "Asynchronously searches for profiles applying filters and pagination."
    )
    public CompletableFuture<ResponseEntity<Object>> searchAsync(@Valid @RequestBody SearchRequestDTO searchRequestDTO) {
        return profileAsyncService.searchAsync(searchRequestDTO)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter profiles (async)",
            description = "Asynchronously retrieves a page of profiles applying specific filters."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ProfileDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getProfilesAsync(
            @PageableDefault(size = 20) Pageable pageable,
            @Valid @RequestBody ProfileFilter filter
    ) {
        return profileAsyncService.getProfilesAsync(pageable, filter)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/track/{trackId}/keyset")
    @Operation(
            summary = "Get profiles by Keyset (async)",
            description = "Asynchronously retrieves a window of profiles using Keyset Pagination."
    )
    public CompletableFuture<ResponseEntity<Object>> getProfilesByKeysetAsync(
            @PathVariable Long trackId,
            @RequestParam(required = false) BigDecimal lastKp,
            @RequestParam(required = false) Long lastId,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        return profileAsyncService.getProfilesByKeysetAsync(trackId, lastKp, lastId, pageSize)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/track/{trackId}/range")
    @Operation(
            summary = "Get profiles by KP range (async)",
            description = "Asynchronously retrieves all profiles of a track between two kilometric points."
    )
    public CompletableFuture<ResponseEntity<Object>> getProfilesByKpRangeAsync(
            @PathVariable Long trackId,
            @RequestParam BigDecimal startKp,
            @RequestParam BigDecimal endKp
    ) {
        return profileAsyncService.getProfilesByKpRangeAsync(trackId, startKp, endKp)
                .thenApply(ResponseEntity::ok);
    }
}
