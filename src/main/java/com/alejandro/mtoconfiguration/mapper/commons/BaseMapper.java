package com.alejandro.mtoconfiguration.mapper.commons;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import org.mapstruct.MappingTarget;
import org.springframework.data.domain.Page;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface BaseMapper<T extends BaseDTO, E extends IEntity> {

    T toDTO(E entity);

    @ToEntityIgnoreAudit
    E toEntity(T dto);

    List<T> toListDTO(List<E> entities);

    @ToEntityIgnoreAudit
    List<E> toListEntity(List<T> dtos);

    void mapToDTOs(List<E> entities, @MappingTarget List<T> dtos);

    default Page<T> mapToDTOs(Page<E> entities) {
        return entities.map(this::toDTO);
    }

    @ToEntityIgnoreAudit
    void updateEntityFromDTO(T dto, @MappingTarget E entity);

    void updateDTOFromEntity(E entity, @MappingTarget T dto);


    /**
     * Establishes a parent-child relationship for a collection of child entities and a single parent entity
     * using the provided linking logic. The `linker` parameter determines how the parent is associated
     * with each child in the collection.
     *
     * @param <C>      the type of the child entity, extending {@link IEntity}
     * @param <P>      the type of the parent entity, extending {@link IEntity}
     * @param children the collection of child entities to associate with the parent; can be null
     * @param parent   the parent entity to associate with the child entities; can be null
     * @param linker   a {@link BiConsumer} implementing the logic to establish the relationship
     *                 between each child entity and the parent entity
     */
    default <C extends IEntity, P extends IEntity> void linkCollection(Collection<C> children, P parent, BiConsumer<C, P> linker) {
        if (children != null && parent != null) {
            children.forEach(child -> linker.accept(child, parent));
        }
    }


    /**
     * Establishes a relationship between a child entity and a parent entity using the provided linking logic.
     * The {@code linker} parameter determines how the parent is associated with the child.
     *
     * @param <C>    the type of the child entity, extending {@link IEntity}
     * @param <P>    the type of the parent entity, extending {@link IEntity}
     * @param child  the child entity to associate with the parent; can be null
     * @param parent the parent entity to associate with the child entity; can be null
     * @param linker a {@link BiConsumer} implementing the logic to establish the relationship
     *               between the child entity and the parent entity
     */
    default <C extends IEntity, P extends IEntity> void linkEntity(C child, P parent, BiConsumer<C, P> linker) {
        if (child != null && parent != null) {
            linker.accept(child, parent);
        }
    }


    /**
     * Establishes a many-to-many relationship between a collection of child entities and a single parent entity.
     * This method utilizes two bi-consumers for bi-directional linking: one to associate the parent with each
     * child entity and another to associate each child entity with the parent.
     *
     * @param <C>          the type of the child entity, extending {@link IEntity}
     * @param <P>          the type of the parent entity, extending {@link IEntity}
     * @param children     the collection of child entities to be linked to the parent; can be null
     * @param parent       the parent entity to be linked to the child entities; can be null
     * @param childLinker  a {@link BiConsumer} defining the linking logic from the child entity to the parent entity
     * @param parentLinker a {@link BiConsumer} defining the linking logic from the parent entity to the child entity
     */
    default <C extends IEntity, P extends IEntity> void linkManyToMany(Collection<C> children, P parent, BiConsumer<C, P> childLinker, BiConsumer<P, C> parentLinker) {
        if (children != null && parent != null) {
            children.forEach(child -> {
                childLinker.accept(child, parent); // Ej: rol.getUsers().add(user)
                parentLinker.accept(parent, child); // Ej: user.getRoles().add(role)
            });
        }
    }


    /**
     * Links a collection of DTOs to a collection of entities by establishing a relationship
     * between the entities and a parent entity. The method supports synchronization by removing
     * any entities not present in the DTO list if specified.
     *
     * @param <D>                        the type of the DTO, extending {@link BaseDTO}
     * @param <C>                        the type of the child entity, extending {@link IEntity}
     * @param <P>                        the type of the parent entity, extending {@link IEntity}
     * @param dtos                       the collection of DTOs to match with the entities; can be null
     * @param entities                   the collection of child entities to link with the parent; can be null
     * @param parent                     the parent entity to associate with the child entities; can be null
     * @param linker                     a {@link BiConsumer} defining the logic to establish the relationship
     *                                   between each child entity and the parent entity
     * @param deleteEntitiesNotInDtoList a flag indicating whether to remove entities from the collection
     *                                   that do not have a corresponding DTO in the provided list
     */
    default <D extends BaseDTO, C extends IEntity, P extends IEntity> void linkCollection(
            Collection<D> dtos,
            Collection<C> entities,
            P parent,
            BiConsumer<C, P> linker,
            boolean deleteEntitiesNotInDtoList
    ) {

        if (entities != null && parent != null) {
            // 1. Sincronización (Borrado de huérfanos)
            if (deleteEntitiesNotInDtoList) {
                Set<Long> dtoIds = (dtos == null) ? Set.of() :
                        dtos.stream()
                                .map(BaseDTO::getId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet());

                // Eliminamos de la colección de la entidad aquellas cuyo ID no esté en el DTO
                entities.removeIf(entity -> entity.getId() != null && !dtoIds.contains(entity.getId()));
            }
            // 2. Vinculación bidireccional
            entities.forEach(child -> linker.accept(child, parent));
        }
    }


    /**
     * Reconcilia una coleccion de hijos contra los DTO recibidos, <b>fusionando por id</b>.
     *
     * <p>Es la alternativa a dejar que MapStruct toque la coleccion. El codigo generado solo sabe
     * hacer dos cosas con una coleccion: reemplazarla entera o ir añadiendo. Añadir duplica —cada
     * hijo que el cliente devuelve nace otra vez porque {@code toEntity} no copia el id— y
     * reemplazar es peor todavia, porque borra e inserta filas que ya existian y con ellas su id,
     * su numero de version y su historico de auditoria.
     *
     * <p>Lo que hace falta es lo que ningun mapeo automatico puede deducir: que un DTO <b>con</b>
     * id se refiere a una fila concreta y hay que volcarlo <b>sobre esa instancia</b>. De ahi los
     * tres pasos:
     *
     * <ol>
     *   <li>se retiran los hijos existentes cuyo id no viene en la peticion (el cliente los ha
     *       quitado; {@code orphanRemoval} los borrara);</li>
     *   <li>cada DTO con id se vuelca sobre el hijo que ya esta en la coleccion, de modo que la
     *       fila se actualiza en lugar de duplicarse;</li>
     *   <li>cada DTO sin id se mapea a un hijo nuevo y se añade.</li>
     * </ol>
     *
     * <p>Un DTO con un id que no pertenece a este padre se ignora: aceptarlo insertaria una fila
     * a partir de datos de otro registro.
     *
     * @param dtos           hijos que manda el cliente; {@code null} equivale a "ninguno", asi que
     *                       vacia la coleccion
     * @param entities       coleccion viva de la entidad padre, que se modifica en el sitio
     * @param parent         entidad padre
     * @param toNewEntity    mapea un DTO sin id a un hijo nuevo
     * @param updateExisting vuelca un DTO sobre el hijo existente que le corresponde
     * @param linker         establece el lado inverso de la relacion en cada hijo
     */
    default <D extends BaseDTO, C extends IEntity, P extends IEntity> void mergeCollection(
            Collection<D> dtos,
            Collection<C> entities,
            P parent,
            Function<D, C> toNewEntity,
            BiConsumer<D, C> updateExisting,
            BiConsumer<C, P> linker
    ) {

        if (entities == null || parent == null) {
            return;
        }

        List<D> incoming = dtos == null ? List.of() : dtos.stream().filter(Objects::nonNull).toList();

        Set<Long> incomingIds = incoming.stream()
                .map(BaseDTO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 1. Los que el cliente ha quitado. Solo se miran los que ya tienen id: un hijo sin id es
        //    uno recien añadido en esta misma peticion y no puede estar en la lista de ids.
        entities.removeIf(entity -> entity.getId() != null && !incomingIds.contains(entity.getId()));

        Map<Long, C> byId = entities.stream()
                .filter(entity -> entity.getId() != null)
                .collect(Collectors.toMap(IEntity::getId, entity -> entity, (a, b) -> a));

        for (D dto : incoming) {
            if (dto.getId() == null) {
                // 3. Alta: hijo nuevo.
                C created = toNewEntity.apply(dto);

                if (created != null) {
                    entities.add(created);
                }

                continue;
            }

            // 2. Modificacion sobre la instancia que ya esta en la coleccion.
            C existing = byId.get(dto.getId());

            if (existing != null) {
                updateExisting.accept(dto, existing);
            }
        }

        entities.forEach(child -> linker.accept(child, parent));
    }


    default <D extends BaseDTO, C extends IEntity, P extends IEntity> void linkManyToMany(
            Collection<D> dtos,
            Collection<C> entities,
            P parent,
            BiConsumer<C, P> childLinker,
            BiConsumer<P, C> parentLinker,
            BiConsumer<P, C> removeLinker,
            boolean removeOldLinks
    ) {

        if (entities != null && parent != null) {
            if (removeOldLinks) {
                Set<Long> dtoIds = (dtos == null) ? Set.of() : dtos.stream()
                        .map(BaseDTO::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                // 1. Identificamos los que hay que borrar (los que tienen ID pero no están en el DTO)
                List<C> toRemove = entities.stream()
                        .filter(entity -> entity.getId() != null && !dtoIds.contains(entity.getId()))
                        .toList();

                // 2. Ejecutamos la función de borrado para cada uno
                toRemove.forEach(child -> removeLinker.accept(parent, child));
                //entities.removeIf(entity -> entity.getId() != null && !dtoIds.contains(entity.getId()));
            }

            // Establecemos vínculos en ambos sentidos
            entities.forEach(child -> {
                childLinker.accept(child, parent);
                parentLinker.accept(parent, child);
            });
        }
    }

    ;
}
