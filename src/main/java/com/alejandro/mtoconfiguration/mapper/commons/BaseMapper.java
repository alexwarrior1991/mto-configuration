package com.alejandro.mtoconfiguration.mapper.commons;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import org.mapstruct.MappingTarget;
import org.springframework.data.domain.Page;

import java.util.List;

public interface BaseMapper<T extends BaseDTO, E extends IEntity> {

    T toDTO(E entity);

    E toEntity(T dto);

    List<T> toListDTO(List<E> entities);

    List<E> toListEntity(List<T> dtos);

    void mapToDTOs(List<E> entities, @MappingTarget List<T> dtos);

    default Page<T> mapToDTOs(Page<E> entities) {
        return entities.map(this::toDTO);
    }

    void updateEntityFromDTO(T dto, @MappingTarget E entity);

    void updateDTOFromEntity(E entity, @MappingTarget T dto);
}
