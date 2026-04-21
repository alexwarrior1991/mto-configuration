package com.alejandro.mtoconfiguration.mapper.commons;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import org.mapstruct.TargetType;
import org.springframework.stereotype.Component;

@Component
public class ReferenceMapper {

    @PersistenceContext
    private EntityManager entityManager;

    public <T extends IEntity> T resolve(Long id, @TargetType Class<T> entityClass) {
        if (id == null) return null;
        return entityManager.getReference(entityClass, id);
    }

}
