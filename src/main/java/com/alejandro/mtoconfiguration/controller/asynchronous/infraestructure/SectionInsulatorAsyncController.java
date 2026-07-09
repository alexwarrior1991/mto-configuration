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
        description = "Operaciones asíncronas para la gestión de aisladores de sección"
)
@ApiResponsesStandard
public class SectionInsulatorAsyncController {

    private final SectionInsulatorAsyncService sectionInsulatorAsyncService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener aislador de sección por ID (async)",
            description = "Recupera de forma asíncrona un aislador de sección mediante su identificador."
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
            summary = "Listar todos los aisladores de sección (async)",
            description = "Recupera de forma asíncrona todos los aisladores de sección."
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync() {
        return sectionInsulatorAsyncService.findAllAsync()
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(
            summary = "Crear aislador de sección (async)",
            description = "Crea de forma asíncrona un nuevo aislador de sección."
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
            summary = "Crear aisladores de sección en bloque (async)",
            description = "Crea varios aisladores de sección de forma asíncrona."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkCreateAsync(@RequestBody List<SectionInsulatorDTO> dtoList) {
        return sectionInsulatorAsyncService.bulkCreateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Actualizar aislador de sección (async)",
            description = "Actualiza de forma asíncrona un aislador de sección existente."
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
            summary = "Actualizar aisladores de sección en bloque (async)",
            description = "Actualiza varios aisladores de sección de forma asíncrona."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkUpdateAsync(@RequestBody List<SectionInsulatorDTO> dtoList) {
        return sectionInsulatorAsyncService.bulkUpdateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Buscar aisladores de sección (async)",
            description = "Busca de forma asíncrona aisladores de sección aplicando filtros y paginación."
    )
    public CompletableFuture<ResponseEntity<Object>> searchAsync(@RequestBody SearchRequestDTO searchRequestDTO) {
        return sectionInsulatorAsyncService.searchAsync(searchRequestDTO)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filtrar aisladores de sección (async)",
            description = "Recupera de forma asíncrona una página de aisladores de sección aplicando filtros específicos."
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
            summary = "Obtener aisladores de sección por ID de estación (async)",
            description = "Recupera de forma asíncrona aisladores de sección asociados a una estación."
    )
    public CompletableFuture<ResponseEntity<Object>> getByStationIdAsync(@PathVariable Long stationId) {
        return sectionInsulatorAsyncService.getSectionInsulatorsByStationIdAsync(stationId)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/station/name/{stationName}")
    @Operation(
            summary = "Obtener aisladores de sección por nombre de estación (async)",
            description = "Recupera de forma asíncrona aisladores de sección por nombre de estación."
    )
    public CompletableFuture<ResponseEntity<Object>> getByStationNameAsync(@PathVariable String stationName) {
        return sectionInsulatorAsyncService.getSectionInsulatorsByStationNameAsync(stationName)
                .thenApply(ResponseEntity::ok);
    }
}
