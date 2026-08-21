package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.CRUDController;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.CantileverFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.CantileverService;
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
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/cantilevers")
@Tag(
        name = "Cantilevers",
        description = "Synchronous operations for cantilever management"
)
public class CantileverController extends CRUDController<CantileverDTO, Cantilever> {

    private final CantileverService cantileverService;

    @Override
    public CantileverService getService() {
        return cantileverService;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get cantilever by ID",
            description = "Retrieves a cantilever by its identifier."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = CantileverDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> getById(@PathVariable Long id) {
        return processGenericRequest(getService()::getById, id);
    }

    @PostMapping
    @Operation(
            summary = "Create cantilever",
            description = "Creates a new cantilever executing validations and business logic."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = CantileverDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> create(@Valid @RequestBody CantileverDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(getService().create(dto));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create cantilevers",
            description = "Creates several cantilevers in a single transactional operation."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkCreate(@Valid @RequestBody List<@Valid CantileverDTO> dtoList) {
        return ResponseEntity.status(HttpStatus.CREATED).body(getService().bulkCreate(dtoList));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update cantilever",
            description = "Updates an existing cantilever by assigning the path ID to the DTO."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = CantileverDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> update(
            @PathVariable Long id,
            @Valid @RequestBody CantileverDTO dto
    ) {
        dto.setId(id);
        return processRequestWithValidation(getService()::update, dto);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update cantilevers",
            description = "Updates several cantilevers in a single transaction."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkUpdate(@Valid @RequestBody List<@Valid CantileverDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkUpdate, dtoList);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete cantilever by ID",
            description = "Performs logical deletion of a cantilever using the path ID."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        CantileverDTO dto = new CantileverDTO();
        dto.setId(id);
        getService().delete(dto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/paged")
    @Operation(
            summary = "List all cantilevers paginated",
            description = "Retrieves a page of cantilevers applying the requested pagination and sorting."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = CantileverDTO.class))
    )
    @Override
    public ResponseEntity<Object> findAll(@PageableDefault(size = 20) Pageable pageable) {
        return processGenericPageRequest(getService()::findAll, pageable);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search cantilevers",
            description = "Searches for cantilevers applying filters, sorting, and generic pagination."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    public ResponseEntity<Object> search(@Valid @RequestBody SearchRequestDTO searchRequestDTO) {
        return processGenericPageRequest(getService()::search, searchRequestDTO);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter cantilevers",
            description = "Retrieves a page of cantilevers applying specific filters."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = CantileverDTO.class))
    )
    public ResponseEntity<Object> getCantilevers(
            @PageableDefault(size = 20) Pageable pageable,
            @Valid @RequestBody CantileverFilter filter
    ) {
        return processGenericPageRequest(f -> getService().getCantilevers(pageable, f), filter);
    }

    @GetMapping("/profile/{profileId}")
    @Operation(
            summary = "Get cantilevers by Profile ID",
            description = "Retrieves all cantilevers associated with a specific profile."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    public ResponseEntity<Object> getByProfileId(@PathVariable Long profileId) {
        return ResponseEntity.ok(getService().getByProfileId(profileId));
    }
}
