package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.CRUDController;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.SteadyArmFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.SteadyArmService;
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
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/steady-arms")
@Tag(
        name = "Steady Arms",
        description = "Synchronous operations for steady arm management"
)
public class SteadyArmController extends CRUDController<SteadyArmDTO, SteadyArm> {

    private final SteadyArmService steadyArmService;

    @Override
    public SteadyArmService getService() {
        return steadyArmService;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get steady arm by ID",
            description = "Retrieves a steady arm by its identifier."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SteadyArmDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> getById(@PathVariable Long id) {
        return processGenericRequest(getService()::getById, id);
    }

    @PostMapping
    @Operation(
            summary = "Create steady arm",
            description = "Creates a new steady arm executing validations and business logic."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SteadyArmDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> create(@Valid @RequestBody SteadyArmDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(getService().create(dto));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create steady arms",
            description = "Creates several steady arms in a single transactional operation."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkCreate(@Valid @RequestBody List<@Valid SteadyArmDTO> dtoList) {
        return ResponseEntity.status(HttpStatus.CREATED).body(getService().bulkCreate(dtoList));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update steady arm",
            description = "Updates an existing steady arm by assigning the path ID to the DTO."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SteadyArmDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> update(
            @PathVariable Long id,
            @Valid @RequestBody SteadyArmDTO dto
    ) {
        dto.setId(id);
        return processRequestWithValidation(getService()::update, dto);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update steady arms",
            description = "Updates several steady arms in a single transaction."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkUpdate(@Valid @RequestBody List<@Valid SteadyArmDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkUpdate, dtoList);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete steady arm by ID",
            description = "Performs logical deletion of a steady arm using the path ID."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        SteadyArmDTO dto = new SteadyArmDTO();
        dto.setId(id);
        getService().delete(dto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search steady arms",
            description = "Searches for steady arms applying filters, sorting, and generic pagination."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    public ResponseEntity<Object> search(@Valid @RequestBody SearchRequestDTO searchRequestDTO) {
        return processGenericPageRequest(getService()::search, searchRequestDTO);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter steady arms",
            description = "Retrieves a page of steady arms applying specific filters."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = SteadyArmDTO.class))
    )
    public ResponseEntity<Object> getSteadyArms(
            @PageableDefault(size = 20) Pageable pageable,
            @Valid @RequestBody SteadyArmFilter filter
    ) {
        return processGenericPageRequest(f -> getService().getSteadyArms(pageable, f), filter);
    }

    @GetMapping("/cantilever/{cantileverId}")
    @Operation(
            summary = "Get steady arm by Cantilever ID",
            description = "Retrieves the steady arm associated with a specific cantilever."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> getByCantileverId(@PathVariable Long cantileverId) {
        return getService().getByCantileverId(cantileverId)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
