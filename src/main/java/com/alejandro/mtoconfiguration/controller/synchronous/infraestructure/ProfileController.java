package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.controller.commons.CRUDController;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ProfileFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/profile")
@Tag(
        name = "Profiles",
        description = "Operaciones síncronas para la gestión de perfiles (postes)"
)
@ApiResponsesStandard
public class ProfileController extends CRUDController<ProfileDTO, Profile> {

    private final ProfileService profileService;

    @Override
    public ProfileService getService() {
        return profileService;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener perfil por ID",
            description = "Recupera un perfil mediante su identificador."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ProfileDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> getById(@PathVariable Long id) {
        return processGenericRequest(getService()::getById, id);
    }

    @PostMapping
    @Operation(
            summary = "Crear perfil",
            description = "Crea un nuevo perfil ejecutando validaciones y lógica de negocio."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ProfileDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> create(@RequestBody ProfileDTO dto) {
        return processRequestWithValidation(getService()::create, dto);
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Crear perfiles en bloque",
            description = "Crea varios perfiles en una única operación transaccional."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkCreate(@RequestBody List<ProfileDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkCreate, dtoList);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Actualizar perfil",
            description = "Actualiza un perfil existente asignando el ID de la ruta al DTO."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ProfileDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> update(
            @PathVariable Long id,
            @RequestBody ProfileDTO dto
    ) {
        dto.setId(id);
        return processRequestWithValidation(getService()::update, dto);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Actualizar perfiles en bloque",
            description = "Actualiza varios perfiles en una única transacción."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkUpdate(@RequestBody List<ProfileDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkUpdate, dtoList);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Eliminar perfil por ID",
            description = "Realiza el borrado lógico de un perfil usando el ID de la ruta."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> deleteById(@PathVariable Long id) {
        ProfileDTO dto = new ProfileDTO();
        dto.setId(id);
        return super.delete(dto);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Buscar perfiles",
            description = "Busca perfiles aplicando filtros, ordenación y paginación genérica."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    public ResponseEntity<Object> search(@RequestBody SearchRequestDTO searchRequestDTO) {
        return processGenericPageRequest(getService()::search, searchRequestDTO);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filtrar perfiles",
            description = "Recupera una página de perfiles aplicando filtros específicos mediante QueryDSL."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ProfileDTO.class))
    )
    public ResponseEntity<Object> getProfiles(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestBody ProfileFilter filter
    ) {
        return processGenericPageRequest(f -> getService().getProfiles(pageable, f), filter);
    }

    @GetMapping("/track/{trackId}/keyset")
    @Operation(
            summary = "Obtener perfiles por Keyset",
            description = "Recupera una ventana de perfiles usando Keyset Pagination para mayor eficiencia en grandes volúmenes."
    )
    public ResponseEntity<Object> getProfilesByKeyset(
            @PathVariable Long trackId,
            @RequestParam(required = false) BigDecimal lastKp,
            @RequestParam(required = false) Long lastId,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        return processGenericListRequestGeneric(id -> getService().getProfilesByKeyset(id, lastKp, lastId, pageSize), trackId);
    }

    @GetMapping("/track/{trackId}/range")
    @Operation(
            summary = "Obtener perfiles por rango de KP",
            description = "Recupera todos los perfiles de una vía comprendidos entre dos puntos kilométricos."
    )
    public ResponseEntity<Object> getProfilesByKpRange(
            @PathVariable Long trackId,
            @RequestParam BigDecimal startKp,
            @RequestParam BigDecimal endKp
    ) {
        return processGenericListRequestGeneric(id -> getService().getProfilesByKpRange(id, startKp, endKp), trackId);
    }
}
