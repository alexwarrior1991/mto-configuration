package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.controller.commons.CRUDController;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ProfileFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileExportService;
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
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/profile")
@Tag(
        name = "Profiles",
        description = "Synchronous operations for profile (poles) management"
)
@ApiResponsesStandard
public class ProfileController extends CRUDController<ProfileDTO, Profile> {

    private final ProfileService profileService;

    private final ProfileExportService profileExportService;

    @Override
    public ProfileService getService() {
        return profileService;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get profile by ID",
            description = "Retrieves a profile by its identifier."
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
            summary = "Create profile",
            description = "Creates a new profile executing validations and business logic."
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
            summary = "Bulk create profiles",
            description = "Creates several profiles in a single transactional operation."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkCreate(@RequestBody List<ProfileDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkCreate, dtoList);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update profile",
            description = "Updates an existing profile by assigning the path ID to the DTO."
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
            summary = "Bulk update profiles",
            description = "Updates several profiles in a single transaction."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkUpdate(@RequestBody List<ProfileDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkUpdate, dtoList);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete profile by ID",
            description = "Performs logical deletion of a profile using the path ID."
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
            summary = "Search profiles",
            description = "Searches for profiles applying filters, sorting, and generic pagination."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    public ResponseEntity<Object> search(@RequestBody SearchRequestDTO searchRequestDTO) {
        return processGenericPageRequest(getService()::search, searchRequestDTO);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter profiles",
            description = "Retrieves a page of profiles applying specific filters using QueryDSL."
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
            summary = "Get profiles by Keyset",
            description = "Retrieves a window of profiles using Keyset Pagination for higher efficiency in large volumes."
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
            summary = "Get profiles by KP range",
            description = "Retrieves all profiles of a track between two kilometric points."
    )
    public ResponseEntity<Object> getProfilesByKpRange(
            @PathVariable Long trackId,
            @RequestParam BigDecimal startKp,
            @RequestParam BigDecimal endKp
    ) {
        return processGenericListRequestGeneric(id -> getService().getProfilesByKpRange(id, startKp, endKp), trackId);
    }

    @GetMapping("/track/{trackId}/export")
    public ResponseEntity<Object> exportProfilesByTrack(
            @PathVariable Long trackId,
            @RequestParam(defaultValue = "basic") String mapperType
    ) {
        Path targetPath = Path.of("exports", "profiles-track-" + trackId + ".csv");

        Function<Profile, String> mapper = switch (mapperType.toLowerCase()) {
            case "technical" -> profileExportService.getTechnicalMapper();
            case "default" -> profileExportService.getDefaultMapper();
            default -> profileExportService.getBasicMapper();
        };

        profileExportService.exportToCsvAsync(trackId, targetPath, mapper);

        return ResponseEntity.accepted().body(
                "Exportación iniciada para la vía " + trackId + ". Archivo destino: " + targetPath
        );
    }
}
