package com.alejandro.mtoconfiguration.masterdata.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MasterDataEventPayloadExtractor {

    private final ObjectMapper objectMapper;

    public Map<String, Object> extract(Object entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }

        Map<String, Object> values = objectMapper.convertValue(
                entity,
                new TypeReference<LinkedHashMap<String, Object>>() {
                }
        );

        return sanitize(values);
    }

    private Map<String, Object> sanitize(Map<String, Object> values) {
        values.remove("hibernateLazyInitializer");
        values.remove("handler");

        return values;
    }

}
