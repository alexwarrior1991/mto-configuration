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
        description = "Operaciones asíncronas para la gestión de seccionadores"
)
public class DisconnectorAsyncController {

    private final DisconnectorAsyncService disconnectorAsyncService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener seccionador por ID (async)",
            description = "Recupera de forma asíncrona un seccionador mediante su identificador."
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
            summary = "Listar todos los seccionadores (async)",
            description = "Recupera de forma asíncrona todos los seccionadores."
    )
    public CompletableFuture<ResponseEntity<Object>> findAllAsync() {
        return disconnectorAsyncService.findAllAsync()
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(
            summary = "Crear seccionador (async)",
            description = "Crea de forma asíncrona un nuevo seccionador."
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
            summary = "Crear seccionadores en bloque (async)",
            description = "Crea varios seccionadores de forma asíncrona."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkCreateAsync(@RequestBody List<DisconnectorDTO> dtoList) {
        return disconnectorAsyncService.bulkCreateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Actualizar seccionador (async)",
            description = "Actualiza de forma asíncrona un seccionador existente."
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
            summary = "Actualizar seccionadores en bloque (async)",
            description = "Actualiza varios seccionadores de forma asíncrona."
    )
    public CompletableFuture<ResponseEntity<Object>> bulkUpdateAsync(@RequestBody List<DisconnectorDTO> dtoList) {
        return disconnectorAsyncService.bulkUpdateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Buscar seccionadores (async)",
            description = "Busca de forma asíncrona seccionadores aplicando filtros y paginación."
    )
    public CompletableFuture<ResponseEntity<Object>> searchAsync(@RequestBody SearchRequestDTO searchRequestDTO) {
        return disconnectorAsyncService.searchAsync(searchRequestDTO)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filtrar seccionadores (async)",
            description = "Recupera de forma asíncrona una página de seccionadores aplicando filtros específicos."
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
            summary = "Obtener seccionadores por ID de estación (async)",
            description = "Recupera de forma asíncrona una lista de seccionadores asociados a una estación específica."
    )
    public CompletableFuture<ResponseEntity<Object>> getByStationIdAsync(@PathVariable Long stationId) {
        return disconnectorAsyncService.getDisconnectorByStationIdAsync(stationId)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/station/name/{stationName}")
    @Operation(
            summary = "Obtener seccionadores por nombre de estación (async)",
            description = "Recupera de forma asíncrona una lista de seccionadores cuyo nombre de estación coincida con el proporcionado."
    )
    public CompletableFuture<ResponseEntity<Object>> getByStationNameAsync(@PathVariable String stationName) {
        return disconnectorAsyncService.getDisconnectorsByStationNameAsync(stationName)
                .thenApply(ResponseEntity::ok);
    }
}
