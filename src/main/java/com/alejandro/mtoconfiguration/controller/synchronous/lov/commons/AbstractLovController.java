package com.alejandro.mtoconfiguration.controller.synchronous.lov.commons;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
public abstract class AbstractLovController<D extends LovDTO> {

    protected abstract LovCrudService<D> getService();

    protected abstract String getResourceName();

    @GetMapping
    @Operation(
            summary = "Find all LOV records",
            description = "Retrieves all records for the configured LOV resource."
    )
    @ApiResponses({
            @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200),
            @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400),
            @ApiResponse(responseCode = ApiConstants.CODE_500, description = ApiConstants.DESC_500)
    })
    public ResponseEntity<List<D>> findAll() {
        return ResponseEntity.ok(getService().findAll());
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Find LOV record by ID",
            description = "Retrieves one LOV record using its database identifier."
    )
    @ApiResponses({
            @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200),
            @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404),
            @ApiResponse(responseCode = ApiConstants.CODE_500, description = ApiConstants.DESC_500)
    })
    public ResponseEntity<D> findById(
            @Parameter(description = "LOV record identifier", required = true, example = "1")
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(getService().findById(id));
    }

    @GetMapping("/code/{code}")
    @Operation(
            summary = "Find LOV record by code",
            description = "Retrieves one LOV record using its business code."
    )
    @ApiResponses({
            @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200),
            @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404),
            @ApiResponse(responseCode = ApiConstants.CODE_500, description = ApiConstants.DESC_500)
    })
    public ResponseEntity<D> findByCode(
            @Parameter(description = "LOV business code", required = true, example = "DEFAULT")
            @PathVariable String code
    ) {
        return ResponseEntity.ok(getService().findByCode(code));
    }

    @PostMapping
    @Operation(
            summary = "Create LOV record",
            description = "Creates a new LOV record after validating the request body."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Resource created successfully.",
                    content = @Content(schema = @Schema(implementation = LovDTO.class))
            ),
            @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400),
            @ApiResponse(responseCode = "409", description = "Conflict. The resource already exists."),
            @ApiResponse(responseCode = "422", description = "Unprocessable entity. Business validation error."),
            @ApiResponse(responseCode = ApiConstants.CODE_500, description = ApiConstants.DESC_500)
    })
    public ResponseEntity<D> create(@Valid @RequestBody D dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(getService().create(dto));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create LOV records",
            description = "Creates several LOV records in a single transactional operation."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Resources created successfully."),
            @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400),
            @ApiResponse(responseCode = "409", description = "Conflict. One or more resources already exist."),
            @ApiResponse(responseCode = "422", description = "Unprocessable entity. Business validation error."),
            @ApiResponse(responseCode = ApiConstants.CODE_500, description = ApiConstants.DESC_500)
    })
    public ResponseEntity<List<D>> bulkCreate(@Valid @RequestBody List<@Valid D> dtos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(getService().bulkCreate(dtos));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update LOV record",
            description = "Updates an existing LOV record. The path ID is the source of truth."
    )
    @ApiResponses({
            @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200),
            @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400),
            @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404),
            @ApiResponse(responseCode = "409", description = "Conflict. Another resource already uses the same code."),
            @ApiResponse(responseCode = "422", description = "Unprocessable entity. Business validation error."),
            @ApiResponse(responseCode = ApiConstants.CODE_500, description = ApiConstants.DESC_500)
    })
    public ResponseEntity<D> update(
            @Parameter(description = "LOV record identifier", required = true, example = "1")
            @PathVariable Long id,
            @Valid @RequestBody D dto
    ) {
        return ResponseEntity.ok(getService().update(id, dto));
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update LOV records",
            description = "Updates several LOV records in a single transactional operation. Each DTO must contain its ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200),
            @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400),
            @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404),
            @ApiResponse(responseCode = "409", description = "Conflict. One or more resources conflict with existing data."),
            @ApiResponse(responseCode = "422", description = "Unprocessable entity. Business validation error."),
            @ApiResponse(responseCode = ApiConstants.CODE_500, description = ApiConstants.DESC_500)
    })
    public ResponseEntity<List<D>> bulkUpdate(@Valid @RequestBody List<@Valid D> dtos) {
        return ResponseEntity.ok(getService().bulkUpdate(dtos));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete LOV record",
            description = "Performs a logical deletion of the LOV record."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Resource deleted successfully."),
            @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404),
            @ApiResponse(responseCode = ApiConstants.CODE_500, description = ApiConstants.DESC_500)
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "LOV record identifier", required = true, example = "1")
            @PathVariable Long id
    ) {
        getService().delete(id);
        return ResponseEntity.noContent().build();
    }


}
