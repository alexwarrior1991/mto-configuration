package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.controller.commons.CRUDController;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/section-insulators")
@Tag(
        name = "Section Insulators",
        description = "Synchronous operations for section insulator management"
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
            summary = "Get section insulator by ID",
            description = "Retrieves a section insulator by its identifier."
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
            summary = "Create section insulator",
            description = "Creates a new section insulator executing validations and business logic."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SectionInsulatorDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> create(@Valid @RequestBody SectionInsulatorDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(getService().create(dto));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create section insulators",
            description = "Creates several section insulators in a single transactional operation."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkCreate(@Valid @RequestBody List<@Valid SectionInsulatorDTO> dtoList) {
        return ResponseEntity.status(HttpStatus.CREATED).body(getService().bulkCreate(dtoList));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update section insulator",
            description = "Updates an existing section insulator by assigning the path ID to the DTO."
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
            @Valid @RequestBody SectionInsulatorDTO dto
    ) {
        dto.setId(id);
        return processRequestWithValidation(getService()::update, dto);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update section insulators",
            description = "Updates several section insulators in a single transaction."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkUpdate(@Valid @RequestBody List<@Valid SectionInsulatorDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkUpdate, dtoList);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete section insulator by ID",
            description = "Performs logical deletion of a section insulator using the path ID."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        SectionInsulatorDTO dto = new SectionInsulatorDTO();
        dto.setId(id);
        getService().delete(dto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/paged")
    @Operation(
            summary = "List all section insulators paginated",
            description = "Retrieves a page of section insulators applying the requested pagination and sorting."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SectionInsulatorDTO.class))
    )
    @Override
    public ResponseEntity<Object> findAll(@PageableDefault(size = 20) Pageable pageable) {
        return processGenericPageRequest(getService()::findAll, pageable);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search section insulators",
            description = "Searches for section insulators applying filters, sorting, and generic pagination."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    public ResponseEntity<Object> search(@Valid @RequestBody SearchRequestDTO searchRequestDTO) {
        return processGenericPageRequest(getService()::search, searchRequestDTO);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter section insulators",
            description = "Retrieves a page of section insulators applying specific filters using QueryDSL."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SectionInsulatorDTO.class))
    )
    public ResponseEntity<Object> getSectionInsulators(
            @PageableDefault(size = 20) Pageable pageable,
            @Valid @RequestBody SectionInsulatorFilter filter
    ) {
        return processGenericPageRequest(f -> getService().getSectionInsulators(pageable, f), filter);
    }

    @GetMapping("/station/{stationId}")
    @Operation(
            summary = "Get section insulators by station ID",
            description = "Retrieves a list of section insulators associated with a specific station."
    )
    public ResponseEntity<Object> getByStationId(@PathVariable Long stationId) {
        return processGenericListRequest(getService()::getSectionInsulatorsByStationId, stationId);
    }

    @GetMapping("/station/name/{stationName}")
    @Operation(
            summary = "Get section insulators by station name",
            description = "Retrieves a list of section insulators whose station name matches the provided one."
    )
    public ResponseEntity<Object> getByStationName(@PathVariable String stationName) {
        return processGenericListRequest(getService()::getSectionInsulatorsByStationName, stationName);
    }
}
