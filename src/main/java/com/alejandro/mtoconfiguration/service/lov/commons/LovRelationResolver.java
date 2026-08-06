package com.alejandro.mtoconfiguration.service.lov.commons;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import jakarta.persistence.EntityNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class LovRelationResolver {

    public <D extends LovDTO, E extends Lov> E resolveRequired(
            D dto,
            LovRepository<E> repository,
            String relationName
    ) {
        if (dto == null) {
            throw new IllegalArgumentException(relationName + " is required");
        }

        E entity = resolveOptional(dto, repository);

        if (entity == null) {
            throw new EntityNotFoundException(relationName + " not found");
        }

        return entity;
    }

    public <D extends LovDTO, E extends Lov> E resolveOptional(
            D dto,
            LovRepository<E> repository
    ) {
        if (dto == null) {
            return null;
        }

        E entity = null;

        if (dto.getId() != null) {
            entity = repository.findById(dto.getId()).orElse(null);
        }

        if (entity == null && StringUtils.isNotBlank(dto.getCode())) {
            entity = repository.findByCode(dto.getCode());
        }

        return entity;
    }

}
