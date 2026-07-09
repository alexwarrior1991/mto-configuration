package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.CRUDController;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.DisconnectorFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.DisconnectorService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/disconnector")
@Tag(
        name = "Disconnectors",
        description = "Operaciones síncronas para la gestión de seccionadores"
)
public class DisconnectorController extends CRUDController<DisconnectorDTO, Disconnector> {

    private final DisconnectorService disconnectorService;

    @Override
    public DisconnectorService getService() {
        return disconnectorService;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener seccionador por ID",
            description = "Recupera un seccionador mediante su identificador."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = DisconnectorDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> getById(@PathVariable Long id) {
        return processGenericRequest(getService()::getById, id);
    }

    @PostMapping
    @Operation(
            summary = "Crear seccionador",
            description = "Crea un nuevo seccionador ejecutando validaciones y lógica de negocio."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = DisconnectorDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> create(@RequestBody DisconnectorDTO dto) {
        return processRequestWithValidation(getService()::create, dto);
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Crear seccionadores en bloque",
            description = "Crea varios seccionadores en una única operación transaccional."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkCreate(@RequestBody List<DisconnectorDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkCreate, dtoList);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Actualizar seccionador",
            description = "Actualiza un seccionador existente asignando el ID de la ruta al DTO."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = DisconnectorDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> update(
            @PathVariable Long id,
            @RequestBody DisconnectorDTO dto
    ) {
        dto.setId(id);
        return processRequestWithValidation(getService()::update, dto);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Actualizar seccionadores en bloque",
            description = "Actualiza varios seccionadores en una única transacción."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkUpdate(@RequestBody List<DisconnectorDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkUpdate, dtoList);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Eliminar seccionador por ID",
            description = "Realiza el borrado lógico de un seccionador usando el ID de la ruta."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> deleteById(@PathVariable Long id) {
        DisconnectorDTO dto = new DisconnectorDTO();
        dto.setId(id);
        return super.delete(dto);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Buscar seccionadores",
            description = "Busca seccionadores aplicando filtros, ordenación y paginación genérica."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    public ResponseEntity<Object> search(@RequestBody SearchRequestDTO searchRequestDTO) {
        return processGenericPageRequest(getService()::search, searchRequestDTO);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filtrar seccionadores",
            description = "Recupera una página de seccionadores aplicando filtros específicos mediante QueryDSL."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = DisconnectorDTO.class))
    )
    public ResponseEntity<Object> getDisconnectors(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestBody DisconnectorFilter filter
    ) {
        return processGenericPageRequest(f -> getService().getDisconnectors(pageable, f), filter);
    }

    @GetMapping("/station/{stationId}")
    @Operation(
            summary = "Obtener seccionadores por ID de estación",
            description = "Recupera una lista de seccionadores asociados a una estación específica."
    )
    public ResponseEntity<Object> getByStationId(@PathVariable Long stationId) {
        return processGenericListRequest(getService()::getDisconnectorByStationId, stationId);
    }

    @GetMapping("/station/name/{stationName}")
    @Operation(
            summary = "Obtener seccionadores por nombre de estación",
            description = "Recupera una lista de seccionadores cuyo nombre de estación coincida con el proporcionado."
    )
    public ResponseEntity<Object> getByStationName(@PathVariable String stationName) {
        return processGenericListRequest(getService()::getDisconnectorsByStationName, stationName);
    }
}
