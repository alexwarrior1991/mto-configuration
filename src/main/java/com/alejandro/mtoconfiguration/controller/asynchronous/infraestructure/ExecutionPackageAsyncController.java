package com.alejandro.mtoconfiguration.controller.asynchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ExecutionPackageFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.asynchronous.ExecutionPackageAsyncService;
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
@RequestMapping(value = "/api/v1/async/execution-package")
@Tag(
        name = "Execution Packages Async",
        description = "Operaciones asíncronas para la gestión de paquetes de ejecución"
)
@ApiResponsesStandard
public class ExecutionPackageAsyncController {

    private final ExecutionPackageAsyncService executionPackageAsyncService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener paquete de ejecución por ID (async)",
            description = "Recupera de forma asíncrona un paquete de ejecución mediante su identificador, ejecutándose en el executor configurado."
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
            summary = "Listar todos los paquetes de ejecución (async)",
            description = "Recupera de forma asíncrona todos los paquetes de ejecución."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    public CompletableFuture<ResponseEntity<Object>> findAllAsync() {
        return executionPackageAsyncService.findAllAsync()
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(
            summary = "Crear paquete de ejecución (async)",
            description = "Crea de forma asíncrona un nuevo paquete de ejecución. Ejecuta validaciones, lógica de negocio, persistencia y limpieza de caché."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ExecutionPackageDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public CompletableFuture<ResponseEntity<Object>> createAsync(@RequestBody ExecutionPackageDTO dto) {
        return executionPackageAsyncService.createAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Crear paquetes de ejecución en bloque (async)",
            description = "Crea varios paquetes de ejecución de forma asíncrona en una única operación transaccional."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public CompletableFuture<ResponseEntity<Object>> bulkCreateAsync(@RequestBody List<ExecutionPackageDTO> dtoList) {
        return executionPackageAsyncService.bulkCreateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Actualizar paquete de ejecución (async)",
            description = "Actualiza de forma asíncrona un paquete existente. El ID de la ruta se asigna al DTO antes de llamar al servicio."
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
            @RequestBody ExecutionPackageDTO dto
    ) {
        dto.setId(id);
        return executionPackageAsyncService.updateAsync(dto)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Actualizar paquetes de ejecución en bloque (async)",
            description = "Actualiza varios paquetes de forma asíncrona en una única transacción. Si uno falla, se hace rollback de todos."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public CompletableFuture<ResponseEntity<Object>> bulkUpdateAsync(@RequestBody List<ExecutionPackageDTO> dtoList) {
        return executionPackageAsyncService.bulkUpdateAsync(dtoList)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Buscar paquetes de ejecución (async)",
            description = "Busca de forma asíncrona paquetes de ejecución aplicando filtros, ordenación y paginación."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public CompletableFuture<ResponseEntity<Object>> searchAsync(@RequestBody SearchRequestDTO searchRequestDTO) {
        return executionPackageAsyncService.searchAsync(searchRequestDTO)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filtrar paquetes de ejecución (async)",
            description = "Recupera de forma asíncrona una página de paquetes de ejecución aplicando filtros específicos mediante QueryDSL."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ExecutionPackageDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public CompletableFuture<ResponseEntity<Object>> getExecutionPackagesAsync(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestBody ExecutionPackageFilter filter
    ) {
        return executionPackageAsyncService.getExecutionPackagesAsync(pageable, filter)
                .thenApply(ResponseEntity::ok);
    }
}
