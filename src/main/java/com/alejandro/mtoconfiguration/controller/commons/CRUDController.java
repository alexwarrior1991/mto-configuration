package com.alejandro.mtoconfiguration.controller.commons;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public abstract class CRUDController<T extends BaseDTO, E extends CRUDEntity>
        implements ReadController<T, E>, ReadAllController<T, E>, SaveController<T, E>, DeleteController<T, E>, SearchController<T, E> {

    /**
     * El historial de cambios de una fila: sus revisiones de Envers, la más reciente primero.
     *
     * <p>Vive aquí, una sola vez, y no en cada maestro: todos lo sirven igual, y un maestro nuevo lo
     * hereda con su ruta. El permiso no es de este método sino de la ruta
     * ({@link ConfigurationApiPaths#REVISIONS_PATTERN} en {@code SecurityConfiguration}):
     * {@code config-audit} además del {@code config-read} de cualquier lectura. Por eso la ruta de
     * un maestro tiene un solo segmento tras el prefijo; lo vigila {@code RevisionsRouteGuardTest}.
     *
     * <p>Se llama {@code /revisions}, como en {@code mto-stock} y {@code mto-maintenance}. El orden
     * que pida {@code pageable} no se mira: el historial solo tiene uno.
     */
    @GetMapping(ConfigurationApiPaths.REVISIONS)
    @Operation(
            summary = "Get the change history of a record",
            description = "Retrieves a page of the audited revisions of one record, newest first: who changed it, "
                    + "when, which operation (CREATED, UPDATED, DELETED) and the record as it was left. "
                    + "Requires config-audit in addition to config-read. A record without revisions is a 404."
    )
    @ApiResponse(responseCode = ApiConstants.CODE_200, description = ApiConstants.DESC_200)
    @ApiResponse(responseCode = ApiConstants.CODE_404, description = ApiConstants.DESC_404)
    public ResponseEntity<Object> revisions(@PathVariable Long id, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(getService().findRevisions(id, pageable));
    }
}
