package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.CRUDController;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
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
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/tracks")
@Tag(
        name = "Tracks",
        description = "Synchronous operations for track management"
)
public class TrackController extends CRUDController<TrackDTO, Track> {

    private final TrackService trackService;

    @Override
    public TrackService getService() {
        return trackService;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get track by ID",
            description = "Retrieves a track by its identifier."
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
            summary = "Create track",
            description = "Creates a new track executing validations and business logic."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> create(@Valid @RequestBody TrackDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(getService().create(dto));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create tracks",
            description = "Creates several tracks in a single transactional operation."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkCreate(@Valid @RequestBody List<@Valid TrackDTO> dtoList) {
        return ResponseEntity.status(HttpStatus.CREATED).body(getService().bulkCreate(dtoList));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update track",
            description = "Updates an existing track by assigning the path ID to the DTO."
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
            @Valid @RequestBody TrackDTO dto
    ) {
        dto.setId(id);
        return processRequestWithValidation(getService()::update, dto);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update tracks",
            description = "Updates several tracks in a single transaction."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkUpdate(@Valid @RequestBody List<@Valid TrackDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkUpdate, dtoList);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete track by ID",
            description = "Performs logical deletion of a track using the path ID."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        TrackDTO dto = new TrackDTO();
        dto.setId(id);
        getService().delete(dto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/paged")
    @Operation(
            summary = "List all tracks paginated",
            description = "Retrieves a page of tracks applying the requested pagination and sorting."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackDTO.class))
    )
    @Override
    public ResponseEntity<Object> findAll(@PageableDefault(size = 20) Pageable pageable) {
        return processGenericPageRequest(getService()::findAll, pageable);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search tracks",
            description = "Searches for tracks applying filters, sorting, and generic pagination."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    public ResponseEntity<Object> search(@Valid @RequestBody SearchRequestDTO searchRequestDTO) {
        return processGenericPageRequest(getService()::search, searchRequestDTO);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter tracks",
            description = "Retrieves a page of tracks applying specific filters using QueryDSL."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = TrackDTO.class))
    )
    public ResponseEntity<Object> getTracks(
            @PageableDefault(size = 20) Pageable pageable,
            @Valid @RequestBody TrackFilter filter
    ) {
        return processGenericPageRequest(f -> getService().getTracks(pageable, f), filter);
    }

}
