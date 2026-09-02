package com.alejandro.mtoconfiguration.service.lov.imports;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Todo lo que el importador necesita saber de una entidad LOV concreta.
 *
 * <p>Existe porque el maestro es una tabla plana con una columna {@code ENTIDAD}: hay
 * que pasar de ese texto al servicio, al repositorio y al DTO correctos. Se resuelve
 * con un registro explicito ({@link LovImportRegistry}) en lugar de con reflexion,
 * porque los DTO no comparten un supertipo con {@code drawingNumber} ni con la
 * relacion al {@code *Type}, y porque una linea por entidad se lee y se depura mucho
 * mejor que un mecanismo generico.
 *
 * <p>Las escrituras van por {@link #service()}, nunca por {@link #repository()}, para
 * que sigan corriendo los ganchos {@code beforeCreate}/{@code beforeUpdate} —que son
 * los que resuelven la relacion obligatoria al {@code *Type}—, la validacion y la
 * auditoria de Envers. El repositorio solo se usa para leer en bloque.
 *
 * @param <D> DTO de la entidad
 * @param <E> entidad JPA
 */
public record LovImportTarget<D extends LovDTO, E extends Lov>(
        String entityName,
        LovCrudService<D> service,
        LovRepository<E> repository,
        Supplier<D> factory,
        BiConsumer<D, Long> drawingNumberSetter,
        BiConsumer<D, String> typeSetter
) {

    public boolean supportsDrawingNumber() {
        return drawingNumberSetter != null;
    }

    public boolean requiresType() {
        return typeSetter != null;
    }

    /** Construye el DTO a partir de los datos ya validados de una fila del maestro. */
    public D newDto(String code, String description, Long drawingNumber, String typeCode) {
        D dto = factory.get();
        dto.setCode(code);
        dto.setDescription(description);
        dto.setEnabled(true);

        if (drawingNumber != null && supportsDrawingNumber()) {
            drawingNumberSetter.accept(dto, drawingNumber);
        }
        if (requiresType()) {
            typeSetter.accept(dto, typeCode);
        }
        return dto;
    }
}
