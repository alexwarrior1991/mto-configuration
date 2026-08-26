package com.alejandro.mtoconfiguration.masterdata.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Traduce una entidad a los valores que viajan en el evento, SIEMPRE a traves de un
 * {@link MasterDataEntityPayloadMapper} explicito.
 * <p>
 * Antes habia un camino por defecto que serializaba la entidad JPA con Jackson. Ese
 * atajo es una trampa: las entidades de este dominio tienen relaciones bidireccionales
 * y ningun {@code @JsonIgnore}, de modo que la serializacion generica entra en
 * recursion infinita; ademas arrastra proxies de Hibernate, dispara consultas N+1 y
 * publica campos tecnicos que nadie ha decidido exponer. Todo eso, encima, dentro de
 * la transaccion de negocio.
 * <p>
 * Falta de mapper es ahora un error explicito y no una sorpresa en tiempo de
 * ejecucion. MasterDataPayloadMapperCoverageTest lo detecta en tiempo de compilacion.
 */
@Component
@RequiredArgsConstructor
public class MasterDataEventPayloadExtractor {

    private final List<MasterDataEntityPayloadMapper<?>> payloadMappers;

    public Map<String, Object> extract(Object entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }

        return findMapper(entity)
                .map(mapper -> mapper.toPayload(entity))
                .orElseThrow(() -> new IllegalStateException(
                        ("No hay MasterDataEntityPayloadMapper para %s. Toda entidad anotada con "
                                + "@PublishMasterDataEvent necesita uno: serializar la entidad JPA "
                                + "directamente provoca recursion infinita en las relaciones "
                                + "bidireccionales.").formatted(entity.getClass().getSimpleName())
                ));
    }

    /**
     * Se busca por tipo exacto y luego por jerarquia. El orden importa: con proxies de
     * Hibernate {@code entity.getClass()} es una subclase generada, pero una entidad
     * que herede de otra mapeada no debe quedarse con el mapper del padre si tiene el
     * suyo propio.
     */
    @SuppressWarnings("unchecked")
    private Optional<MasterDataEntityPayloadMapper<Object>> findMapper(Object entity) {
        Class<?> entityClass = entity.getClass();

        return payloadMappers.stream()
                .filter(mapper -> mapper.supportedType().equals(entityClass))
                .findFirst()
                .or(() -> payloadMappers.stream()
                        .filter(mapper -> mapper.supportedType().isAssignableFrom(entityClass))
                        .findFirst())
                .map(mapper -> (MasterDataEntityPayloadMapper<Object>) mapper);
    }
}
