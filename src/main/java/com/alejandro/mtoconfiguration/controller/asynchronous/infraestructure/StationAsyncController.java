package com.alejandro.mtoconfiguration.controller.asynchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.StationFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.asynchronous.StationAsyncService;
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
@RequestMapping(value = "/api/v1/async/station")
@Tag(
        name = "Stations Async",
        description = "Operaciones asíncronas para la gestión de estaciones"
)
@ApiResponsesStandard
public class StationAsyncController {

    private final StationAsyncService stationAsyncService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener estación por ID (async)",
            description = "Recupera de forma asíncrona una estación mediante su identificador."
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
            summary = "Listar todas las estaciones (async)",
            description = "Recupera de forma asíncrona todas las estaciones."
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync() {
        return stationAsyncService.findAllAsync()
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(
            summary = "Crear estación (async)",
            description = "Crea de forma asíncrona una nueva estación."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = StationDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> createAsync(@RequestBody StationDTO dto) {
        return stationAsyncService.createAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Crear estaciones en bloque (async)",
            description = "Crea varias estaciones de forma asíncrona."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkCreateAsync(@RequestBody List<StationDTO> dtoList) {
        return stationAsyncService.bulkCreateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Actualizar estación (async)",
            description = "Actualiza de forma asíncrona una estación existente."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = StationDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> updateAsync(
            @PathVariable Long id,
            @RequestBody StationDTO dto
    ) {
        dto.setId(id);
        return stationAsyncService.updateAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Actualizar estaciones en bloque (async)",
            description = "Actualiza varias estaciones de forma asíncrona."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkUpdateAsync(@RequestBody List<StationDTO> dtoList) {
        return stationAsyncService.bulkUpdateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Buscar estaciones (async)",
            description = "Busca de forma asíncrona estaciones aplicando filtros y paginación."
    )
    public CompletableFuture<ResponseEntity<Object>> searchAsync(@RequestBody SearchRequestDTO searchRequestDTO) {
        return stationAsyncService.searchAsync(searchRequestDTO)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filtrar estaciones (async)",
            description = "Recupera de forma asíncrona una página de estaciones aplicando filtros específicos."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = StationDTO.class))
    )
    public CompletableFuture<ResponseEntity<Object>> getStationsAsync(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestBody StationFilter filter
    ) {
        return stationAsyncService.getStationsAsync(pageable, filter)
                .thenApply(ResponseEntity::ok);
    }


}
