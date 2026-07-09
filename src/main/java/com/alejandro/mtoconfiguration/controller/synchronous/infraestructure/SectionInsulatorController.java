package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.controller.commons.CRUDController;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.SectionInsulatorFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.SectionInsulatorService;
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
@RequestMapping(value = "/api/v1/section-insulator")
@Tag(
        name = "Section Insulators",
        description = "Operaciones síncronas para la gestión de aisladores de sección"
)
@ApiResponsesStandard
public class SectionInsulatorController extends CRUDController<SectionInsulatorDTO, SectionInsulator> {

    private final SectionInsulatorService sectionInsulatorService;

    @Override
    public SectionInsulatorService getService() {
        return sectionInsulatorService;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener aislador de sección por ID",
            description = "Recupera un aislador de sección mediante su identificador."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SectionInsulatorDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> getById(@PathVariable Long id) {
        return processGenericRequest(getService()::getById, id);
    }

    @PostMapping
    @Operation(
            summary = "Crear aislador de sección",
            description = "Crea un nuevo aislador de sección ejecutando validaciones y lógica de negocio."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SectionInsulatorDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> create(@RequestBody SectionInsulatorDTO dto) {
        return processRequestWithValidation(getService()::create, dto);
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Crear aisladores de sección en bloque",
            description = "Crea varios aisladores de sección en una única operación transaccional."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkCreate(@RequestBody List<SectionInsulatorDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkCreate, dtoList);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Actualizar aislador de sección",
            description = "Actualiza un aislador de sección existente asignando el ID de la ruta al DTO."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SectionInsulatorDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> update(
            @PathVariable Long id,
            @RequestBody SectionInsulatorDTO dto
    ) {
        dto.setId(id);
        return processRequestWithValidation(getService()::update, dto);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Actualizar aisladores de sección en bloque",
            description = "Actualiza varios aisladores de sección en una única transacción."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkUpdate(@RequestBody List<SectionInsulatorDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkUpdate, dtoList);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Eliminar aislador de sección por ID",
            description = "Realiza el borrado lógico de un aislador de sección usando el ID de la ruta."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> deleteById(@PathVariable Long id) {
        SectionInsulatorDTO dto = new SectionInsulatorDTO();
        dto.setId(id);
        return super.delete(dto);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Buscar aisladores de sección",
            description = "Busca aisladores de sección aplicando filtros, ordenación y paginación genérica."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    public ResponseEntity<Object> search(@RequestBody SearchRequestDTO searchRequestDTO) {
        return processGenericPageRequest(getService()::search, searchRequestDTO);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filtrar aisladores de sección",
            description = "Recupera una página de aisladores de sección aplicando filtros específicos mediante QueryDSL."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SectionInsulatorDTO.class))
    )
    public ResponseEntity<Object> getSectionInsulators(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestBody SectionInsulatorFilter filter
    ) {
        return processGenericPageRequest(f -> getService().getSectionInsulators(pageable, f), filter);
    }

    @GetMapping("/station/{stationId}")
    @Operation(
            summary = "Obtener aisladores de sección por ID de estación",
            description = "Recupera una lista de aisladores de sección asociados a una estación específica."
    )
    public ResponseEntity<Object> getByStationId(@PathVariable Long stationId) {
        return processGenericListRequest(getService()::getSectionInsulatorsByStationId, stationId);
    }

    @GetMapping("/station/name/{stationName}")
    @Operation(
            summary = "Obtener aisladores de sección por nombre de estación",
            description = "Recupera una lista de aisladores de sección cuyo nombre de estación coincida con el proporcionado."
    )
    public ResponseEntity<Object> getByStationName(@PathVariable String stationName) {
        return processGenericListRequest(getService()::getSectionInsulatorsByStationName, stationName);
    }
}
