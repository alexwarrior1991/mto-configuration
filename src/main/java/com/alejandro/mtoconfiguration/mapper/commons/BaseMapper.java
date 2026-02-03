package com.alejandro.mtoconfiguration.mapper.commons;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import org.mapstruct.MappingTarget;

import java.util.List;

public interface BaseMapper<T extends BaseDTO, E extends IEntity> {

    T toDTO(E entity);

    E toEntity(T dto);

    List<T> toListDTO(List<E> entities);

    List<E> toListEntity(List<T> dtos);

    void updateEntityFromDTO(T dto, @MappingTarget E entity);
}
