package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.CRUDController;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.TrackFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.TrackService;
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
@RequestMapping(value = "/api/v1/track")
@Tag(
        name = "Tracks",
        description = "Operaciones síncronas para la gestión de vías"
)
public class TrackController extends CRUDController<TrackDTO, Track> {

    private final TrackService trackService;

    @Override
    public TrackService getService() {
        return trackService;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener vía por ID",
            description = "Recupera una vía mediante su identificador."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> getById(@PathVariable Long id) {
        return processGenericRequest(getService()::getById, id);
    }

    @PostMapping
    @Operation(
            summary = "Crear vía",
            description = "Crea una nueva vía ejecutando validaciones y lógica de negocio."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> create(@RequestBody TrackDTO dto) {
        return processRequestWithValidation(getService()::create, dto);
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Crear vías en bloque",
            description = "Crea varias vías en una única operación transaccional."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkCreate(@RequestBody List<TrackDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkCreate, dtoList);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Actualizar vía",
            description = "Actualiza una vía existente asignando el ID de la ruta al DTO."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> update(
            @PathVariable Long id,
            @RequestBody TrackDTO dto
    ) {
        dto.setId(id);
        return processRequestWithValidation(getService()::update, dto);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Actualizar vías en bloque",
            description = "Actualiza varias vías en una única transacción."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkUpdate(@RequestBody List<TrackDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkUpdate, dtoList);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Eliminar vía por ID",
            description = "Realiza el borrado lógico de una vía usando el ID de la ruta."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> deleteById(@PathVariable Long id) {
        TrackDTO dto = new TrackDTO();
        dto.setId(id);
        return super.delete(dto);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Buscar vías",
            description = "Busca vías aplicando filtros, ordenación y paginación genérica."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    public ResponseEntity<Object> search(@RequestBody SearchRequestDTO searchRequestDTO) {
        return processGenericPageRequest(getService()::search, searchRequestDTO);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filtrar vías",
            description = "Recupera una página de vías aplicando filtros específicos mediante QueryDSL."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackDTO.class))
    )
    public ResponseEntity<Object> getTracks(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestBody TrackFilter filter
    ) {
        return processGenericPageRequest(f -> getService().getTracks(pageable, f), filter);
    }

}
