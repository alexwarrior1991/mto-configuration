package com.alejandro.mtoconfiguration.controller.commons;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import org.springframework.http.ResponseEntity;

/**
 * Atajos de guardado para los controladores CRUD.
 *
 * <p><b>Ojo al declarar metodos aqui.</b> Bean Validation prohibe que un metodo que sobrescribe a
 * otro añada restricciones a sus parametros (regla HV000151 / seccion 5.6.5 de la especificacion).
 * Como todos los controladores concretos redeclaran estos metodos para colgarles su
 * {@code @PostMapping} y su {@code @Valid @RequestBody}, cualquier firma que aparezca en esta
 * interfaz se convierte en una firma que los hijos ya no pueden anotar.
 *
 * <p>Con un parametro simple ({@code @Valid @RequestBody T dto}) el conflicto no llega a saltar
 * porque de esa validacion se encarga el resolutor de argumentos. Con una lista de elementos
 * validados ({@code List<@Valid T>}) si: esa restriccion de elemento de contenedor obliga a Spring
 * a validar el metodo, Hibernate Validator construye los metadatos de la clase entera y aborta con
 * {@code ConstraintDeclarationException}, que sale por HTTP como un 500. Por eso {@code create},
 * {@code bulkCreate} y {@code bulkUpdate} <b>no</b> viven en esta interfaz: solo existen en cada
 * controlador concreto, que es donde llevan sus anotaciones.
 */
public interface SaveController<T extends BaseDTO, E extends IEntity> extends BaseController<T, E> {

    default ResponseEntity<Object> update(T dto) {
        return processRequestWithValidation(getService()::update, dto);
    }

}
