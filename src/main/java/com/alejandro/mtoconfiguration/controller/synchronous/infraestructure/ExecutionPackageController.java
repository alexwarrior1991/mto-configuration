package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.controller.commons.CRUDController;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.ExecutionPackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/execution-package")
@Tag(
        name = "Execution Packages",
        description = "Operaciones síncronas para la gestión de paquetes de ejecución"
)
@ApiResponsesStandard
public class ExecutionPackageController extends CRUDController<ExecutionPackageDTO, ExecutionPackage> {

    private final ExecutionPackageService executionPackageService;


    @Override
    public ExecutionPackageService getService() {
        return executionPackageService;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener paquete de ejecución por ID",
            description = "Recupera un paquete de ejecución mediante su identificador. Usa la caché configurada en el servicio."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ExecutionPackageDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> getById(@PathVariable Long id) {
        return processGenericRequest(getService()::getById, id);
    }

    @PostMapping
    @Operation(
            summary = "Crear paquete de ejecución",
            description = "Crea un nuevo paquete de ejecución. Ejecuta validaciones, lógica de negocio, persistencia y limpieza de caché."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ExecutionPackageDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> create(@RequestBody ExecutionPackageDTO dto) {
        return processRequestWithValidation(getService()::create, dto);
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Crear paquetes de ejecución en bloque",
            description = "Crea varios paquetes de ejecución en una única operación transaccional."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkCreate(@RequestBody List<ExecutionPackageDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkCreate, dtoList);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Actualizar paquete de ejecución",
            description = "Actualiza un paquete de ejecución existente. El ID de la ruta se asigna al DTO antes de llamar al servicio."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ExecutionPackageDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> update(
            @PathVariable Long id,
            @RequestBody ExecutionPackageDTO dto
    ) {
        dto.setId(id);
        return processRequestWithValidation(getService()::update, dto);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Actualizar paquetes de ejecución en bloque",
            description = "Actualiza varios paquetes de ejecución en una única transacción. Si uno falla, se hace rollback de todos."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkUpdate(@RequestBody List<ExecutionPackageDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkUpdate, dtoList);
    }

    @DeleteMapping
    @Operation(
            summary = "Eliminar paquete de ejecución",
            description = "Realiza el borrado lógico de un paquete de ejecución usando el ID recibido en el cuerpo."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> delete(@RequestBody ExecutionPackageDTO dto) {
        return super.delete(dto);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Eliminar paquete de ejecución por ID",
            description = "Realiza el borrado lógico de un paquete de ejecución usando el ID de la ruta."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> deleteById(@PathVariable Long id) {
        ExecutionPackageDTO dto = new ExecutionPackageDTO();
        dto.setId(id);
        return super.delete(dto);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Buscar paquetes de ejecución",
            description = "Busca paquetes de ejecución aplicando filtros, ordenación y paginación."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> search(@RequestBody SearchRequestDTO searchRequestDTO) {
        return processGenericPageRequest(getService()::search, searchRequestDTO);
    }
}
