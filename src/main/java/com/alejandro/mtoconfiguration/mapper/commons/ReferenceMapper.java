package com.alejandro.mtoconfiguration.mapper.commons;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.mapstruct.TargetType;
import org.springframework.stereotype.Component;

@Component
public class ReferenceMapper {

    @PersistenceContext
    private EntityManager entityManager;

    public <T> T resolve(Long id, @TargetType Class<T> entityClass) {
        if (id == null) return null;
        return entityManager.getReference(entityClass, id);
    }

}
