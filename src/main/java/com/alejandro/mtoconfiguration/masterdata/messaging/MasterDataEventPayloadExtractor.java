package com.alejandro.mtoconfiguration.masterdata.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MasterDataEventPayloadExtractor {

    private final ObjectMapper objectMapper;
    private final List<MasterDataEntityPayloadMapper<?>> payloadMappers;

    public Map<String, Object> extract(Object entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }

        Optional<MasterDataEntityPayloadMapper<Object>> mapper = findMapper(entity);

        if (mapper.isPresent()) {
            return mapper.get().toPayload(entity);
        }

        return extractDefault(entity);
    }

    @SuppressWarnings("unchecked")
    private Optional<MasterDataEntityPayloadMapper<Object>> findMapper(Object entity) {
        return payloadMappers.stream()
                .filter(mapper -> mapper.supportedType().isAssignableFrom(entity.getClass()))
                .map(mapper -> (MasterDataEntityPayloadMapper<Object>) mapper)
                .findFirst();
    }

    private Map<String, Object> extractDefault(Object entity) {
        Map<String, Object> values = objectMapper.convertValue(
                entity,
                new TypeReference<LinkedHashMap<String, Object>>() {
                }
        );

        values.remove("hibernateLazyInitializer");
        values.remove("handler");

        return values;
    }

}
