package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ApiResponsesStandard;
import com.alejandro.mtoconfiguration.controller.commons.CRUDController;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ExecutionPackageFilter;
import com.alejandro.mtoconfiguration.service.infraestructure.ExecutionPackageService;
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
@RequestMapping(value = "/api/v1/execution-package")
@Tag(
        name = "Execution Packages",
        description = "Synchronous operations for execution package management"
)
@ApiResponsesStandard
public class ExecutionPackageController extends CRUDController<ExecutionPackageDTO, ExecutionPackage> {

    private final ExecutionPackageService executionPackageService;


    @Override
    public ExecutionPackageService getService() {
        return executionPackageService;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get execution package by ID",
            description = "Retrieves an execution package by its identifier. Uses the configured cache in the service."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ExecutionPackageDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> getById(@PathVariable Long id) {
        return processGenericRequest(getService()::getById, id);
    }

    @PostMapping
    @Operation(
            summary = "Create execution package",
            description = "Creates a new execution package. Executes validations, business logic, persistence, and cache cleanup."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ExecutionPackageDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> create(@RequestBody ExecutionPackageDTO dto) {
        return processRequestWithValidation(getService()::create, dto);
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create execution packages",
            description = "Creates several execution packages in a single transactional operation."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkCreate(@RequestBody List<ExecutionPackageDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkCreate, dtoList);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update execution package",
            description = "Updates an existing execution package. The path ID is assigned to the DTO before calling the service."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ExecutionPackageDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> update(
            @PathVariable Long id,
            @RequestBody ExecutionPackageDTO dto
    ) {
        dto.setId(id);
        return processRequestWithValidation(getService()::update, dto);
    }

    @PutMapping("/bulk")
    @Operation(
            summary = "Bulk update execution packages",
            description = "Updates several execution packages in a single transaction. If one fails, all are rolled back."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> bulkUpdate(@RequestBody List<ExecutionPackageDTO> dtoList) {
        return processBulkRequestWithValidation(getService()::bulkUpdate, dtoList);
    }

    @DeleteMapping
    @Operation(
            summary = "Delete execution package",
            description = "Performs logical deletion of an execution package using the ID received in the body."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> delete(@RequestBody ExecutionPackageDTO dto) {
        return super.delete(dto);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete execution package by ID",
            description = "Performs logical deletion of an execution package using the path ID."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> deleteById(@PathVariable Long id) {
        ExecutionPackageDTO dto = new ExecutionPackageDTO();
        dto.setId(id);
        return super.delete(dto);
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search execution packages",
            description = "Searches for execution packages applying filters, sorting, and pagination."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> search(@RequestBody SearchRequestDTO searchRequestDTO) {
        return processGenericPageRequest(getService()::search, searchRequestDTO);
    }

    @PostMapping("/filter")
    @Operation(
            summary = "Filter execution packages",
            description = "Retrieves a page of execution packages applying specific filters (name, company, dates, free text) using QueryDSL."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = ExecutionPackageDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_400, description = ApiConstants.DESC_400)
    public ResponseEntity<Object> getExecutionPackages(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestBody ExecutionPackageFilter filter
    ) {
        return processGenericPageRequest(f -> getService().getExecutionPackages(pageable, f), filter);
    }
}
