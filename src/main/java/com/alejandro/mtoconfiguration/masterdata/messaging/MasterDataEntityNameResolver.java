package com.alejandro.mtoconfiguration.masterdata.messaging;

import org.springframework.stereotype.Component;

@Component
public class MasterDataEntityNameResolver {


    public String resolve(Object entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }

        PublishMasterDataEvent annotation = entity.getClass().getAnnotation(PublishMasterDataEvent.class);
        if (annotation != null && !annotation.name().isBlank()) {
            return normalize(annotation.name());
        }

        return normalize(entity.getClass().getSimpleName());
    }

    public String normalize(String value) {
        return value.trim()
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .toLowerCase()
                .replace("_", "-")
                .replace(" ", "-");
    }

}
