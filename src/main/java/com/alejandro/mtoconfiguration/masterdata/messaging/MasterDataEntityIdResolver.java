package com.alejandro.mtoconfiguration.masterdata.messaging;

import jakarta.persistence.Id;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
        Field idField = findIdField(entity.getClass());
        if (idField != null) {
            return readField(idField, entity);
        }

        // Acceso por propiedad: todas las entidades publicables de este proyecto
        // anotan @Id sobre el getter (ver CantileverRepository), no sobre el campo.
        // Un campo nunca expone la anotacion puesta en su getter via reflexion: son
        // elementos reflejados distintos, asi que hace falta mirar tambien los metodos.
        Method idGetter = findIdGetter(entity.getClass());
        if (idGetter != null) {
            return invokeGetter(idGetter, entity);
        }

        throw new IllegalArgumentException(
                "No field or getter annotated with @Id found in " + entity.getClass().getSimpleName());
    }

    private Field findIdField(Class<?> entityClass) {
        Class<?> currentClass = entityClass;

        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    return field;
                }
            }

            currentClass = currentClass.getSuperclass();
        }

        return null;
    }

    private Method findIdGetter(Class<?> entityClass) {
        Class<?> currentClass = entityClass;

        while (currentClass != null && currentClass != Object.class) {
            for (Method method : currentClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Id.class)) {
                    return method;
                }
            }

            currentClass = currentClass.getSuperclass();
        }

        return null;
    }

    private Object readField(Field field, Object entity) {
        try {
            field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read entity id field", exception);
        }
    }

    private Object invokeGetter(Method method, Object entity) {
        try {
            method.setAccessible(true);
            return method.invoke(entity);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Cannot read entity id via getter", exception);
        }
    }
}
