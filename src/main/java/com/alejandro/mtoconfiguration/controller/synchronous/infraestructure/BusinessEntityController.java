package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.ApiConstants;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.BusinessEntityDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.BusinessEntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Las empresas a las que apunta {@code companyId} de un paquete de ejecucion, solo lectura: se
 * cargan con el maestro de perfiles, no por la API, pero un cliente tiene que poder elegir una al
 * dar de alta o modificar un paquete. No extiende {@code CRUDController} a proposito: sin alta,
 * modificacion, borrado ni busqueda.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/business-entities")
@Tag(
        name = "Business Entities",
        description = "Read-only access to the companies an execution package can reference"
)
public class BusinessEntityController {

    private final BusinessEntityService service;

    @GetMapping
    @Operation(
            summary = "List business entities",
            description = "Every company an execution package can reference through companyId."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    public ResponseEntity<List<BusinessEntityDTO>> findAll() throws BaseException {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get business entity by ID",
            description = "Retrieves a company by its identifier."
    )
    @ApiResponse(
            responseCode = ApiConstants.CODE_200,
            description = ApiConstants.DESC_200,
            content = @Content(schema = @Schema(implementation = BusinessEntityDTO.class))
    )
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<BusinessEntityDTO> getById(@PathVariable Long id) throws BaseException {
        return ResponseEntity.ok(service.getById(id));
    }
}
