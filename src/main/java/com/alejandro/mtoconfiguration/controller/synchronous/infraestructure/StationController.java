package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.CRUDController;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.StationFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.StationService;
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
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/stations")
@Tag(
        name = "Stations",
        description = "Synchronous operations for station management"
)
public class StationController extends CRUDController<StationDTO, Station> {

    private final StationService stationService;


    @Override
    public StationService getService() {
        return stationService;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get station by ID",
            description = "Retrieves a station by its identifier."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = StationDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> getById(@PathVariable Long id) {
        return processGenericRequest(getService()::getById, id);
    }

    @PostMapping
    @Operation(
            summary = "Create station",
            description = "Creates a new station executing validations and business logic."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = StationDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> create(@Valid @RequestBody StationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(getService().create(dto));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create stations",
            description = "Creates several stations in a single transactional operation."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkCreate(@Valid @RequestBody List<@Valid StationDTO> dtoList) {
        return ResponseEntity.status(HttpStatus.CREATED).body(getService().bulkCreate(dtoList));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update station",
            description = "Updates an existing station by assigning the path ID to the DTO."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = StationDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> update(
            @PathVariable Long id,
            @Valid @RequestBody StationDTO dto
    ) {
        dto.setId(id);
        return processRequestWithValidation(getService()::update, dto);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update stations",
            description = "Updates several stations in a single transaction."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkUpdate(@Valid @RequestBody List<@Valid StationDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkUpdate, dtoList);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete station by ID",
            description = "Performs logical deletion of a station using the path ID."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        StationDTO dto = new StationDTO();
        dto.setId(id);
        getService().delete(dto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/paged")
    @Operation(
            summary = "List all stations paginated",
            description = "Retrieves a page of stations applying the requested pagination and sorting."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = StationDTO.class))
    )
    @Override
    public ResponseEntity<Object> findAll(@PageableDefault(size = 20) Pageable pageable) {
        return processGenericPageRequest(getService()::findAll, pageable);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search stations",
            description = "Searches for stations applying filters, sorting, and generic pagination."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    public ResponseEntity<Object> search(@Valid @RequestBody SearchRequestDTO searchRequestDTO) {
        return processGenericPageRequest(getService()::search, searchRequestDTO);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter stations",
            description = "Retrieves a page of stations applying specific filters (name, package, tracks) using QueryDSL."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = StationDTO.class))
    )
    public ResponseEntity<Object> getStations(
            @PageableDefault(size = 20) Pageable pageable,
            @Valid @RequestBody StationFilter filter
    ) {
        return processGenericPageRequest(f -> getService().getStations(pageable, f), filter);
    }


}
