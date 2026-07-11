package com.alejandro.mtoconfiguration.masterdata.messaging;

import jakarta.persistence.Id;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Component
public class MasterDataEntityIdResolver {

    public String resolve(Object entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }

        Object id = findIdValue(entity);

        if (id == null) {
            throw new IllegalArgumentException("Entity id cannot be null for " + entity.getClass().getSimpleName());
        }

        return String.valueOf(id);
    }

    private Object findIdValue(Object entity) {
        Class<?> currentClass = entity.getClass();

        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    return readField(field, entity);
                }
            }

            currentClass = currentClass.getSuperclass();
        }

        throw new IllegalArgumentException("No field annotated with @Id found in " + entity.getClass().getSimpleName());
    }

    private Object readField(Field field, Object entity) {
        try {
            field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read entity id field", exception);
        }
    }
}
