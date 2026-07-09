package com.alejandro.mtoconfiguration.controller.asynchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ProfileFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.asynchronous.ProfileAsyncService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/async/profile")
@Tag(
        name = "Profiles Async",
        description = "Operaciones asíncronas para la gestión de perfiles (postes)"
)
@ApiResponsesStandard
public class ProfileAsyncController {

    private final ProfileAsyncService profileAsyncService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener perfil por ID (async)",
            description = "Recupera de forma asíncrona un perfil mediante su identificador."
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
            summary = "Listar todos los perfiles (async)",
            description = "Recupera de forma asíncrona todos los perfiles."
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync() {
        return profileAsyncService.findAllAsync()
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(
            summary = "Crear perfil (async)",
            description = "Crea de forma asíncrona un nuevo perfil."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ProfileDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> createAsync(@RequestBody ProfileDTO dto) {
        return profileAsyncService.createAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Crear perfiles en bloque (async)",
            description = "Crea varios perfiles de forma asíncrona."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkCreateAsync(@RequestBody List<ProfileDTO> dtoList) {
        return profileAsyncService.bulkCreateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Actualizar perfil (async)",
            description = "Actualiza de forma asíncrona un perfil existente."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ProfileDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> updateAsync(
            @PathVariable Long id,
            @RequestBody ProfileDTO dto
    ) {
        dto.setId(id);
        return profileAsyncService.updateAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Actualizar perfiles en bloque (async)",
            description = "Actualiza varios perfiles de forma asíncrona."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkUpdateAsync(@RequestBody List<ProfileDTO> dtoList) {
        return profileAsyncService.bulkUpdateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Buscar perfiles (async)",
            description = "Busca de forma asíncrona perfiles aplicando filtros y paginación."
    )
    public CompletableFuture<ResponseEntity<Object>> searchAsync(@RequestBody SearchRequestDTO searchRequestDTO) {
        return profileAsyncService.searchAsync(searchRequestDTO)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filtrar perfiles (async)",
            description = "Recupera de forma asíncrona una página de perfiles aplicando filtros específicos."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ProfileDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getProfilesAsync(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestBody ProfileFilter filter
    ) {
        return profileAsyncService.getProfilesAsync(pageable, filter)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/track/{trackId}/keyset")
    @Operation(
            summary = "Obtener perfiles por Keyset (async)",
            description = "Recupera de forma asíncrona una ventana de perfiles usando Keyset Pagination."
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
            summary = "Obtener perfiles por rango de KP (async)",
            description = "Recupera de forma asíncrona todos los perfiles de una vía comprendidos entre dos puntos kilométricos."
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
