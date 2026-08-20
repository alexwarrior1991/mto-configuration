package com.alejandro.mtoconfiguration.configuration.cache;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Component("redisCacheKeyGenerator")
public class RedisCacheKeyGenerator implements KeyGenerator {

    private final ObjectMapper objectMapper;

    private static final String ORDER_INSENSITIVE_NODE = "filters";

    public RedisCacheKeyGenerator(@Qualifier("redisCacheKeyObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    /**
     * Construye la clave con el mismo formato que usa el generador automático.
     * Se expone para que los servicios que delegan la caché en otro bean puedan
     * reutilizar exactamente la misma clave y los patrones de invalidación sigan casando.
     */
    public String buildKey(Object target, String methodName, Object... params) {
        String serviceName = getServiceName(target);
        String paramsKey = buildParamsKey(params);

        return paramsKey.isBlank()
                ? serviceName + ":" + methodName
                : serviceName + ":" + methodName + ":" + paramsKey;
    }

    @Override
    public Object generate(Object target, Method method, @Nullable Object... params) {
        return buildKey(target, method.getName(), params);
    }

    private String getServiceName(Object target) {
        return AopUtils.getTargetClass(target).getSimpleName();
    }

    private String buildParamsKey(Object[] params) {
        if (params == null || params.length == 0) {
            return "";
        }

        return Arrays.stream(params)
                .map(this::normalizeParam)
                .collect(Collectors.joining(":"));
    }

    private String normalizeParam(Object param) {
        if (param == null) {
            return "null";
        }

        if (param instanceof Pageable pageable) {
            return normalizePageable(pageable);
        }

        if (isSimpleValue(param)) {
            return String.valueOf(param);
        }

        return sha256(toJson(param));

    }

    private String normalizePageable(Pageable pageable) {
        String sort = pageable.getSort().stream()
                .map(order -> order.getProperty() + "," + order.getDirection().name())
                .collect(Collectors.joining(";"));

        if (sort.isBlank()) {
            sort = "unsorted";
        }

        return "page=" + pageable.getPageNumber()
                + ":size=" + pageable.getPageSize()
                + ":sort=" + sort;
    }

    private boolean isSimpleValue(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>;
    }

    /**
     * Deja el árbol en forma canónica antes de hashearlo.
     * <p>
     * Los arrays que cuelgan de "filters" son cláusulas IN: [1,2] y [2,1] significan
     * lo mismo, así que se ordenan para que no generen dos entradas en Redis.
     * <p>
     * Fuera de "filters" el orden NO se toca. En PageableDTO, sortBy y sortDirection
     * son listas paralelas —sortBy[i] va con sortDirection[i]— y además ordenar por
     * name y luego kp no es lo mismo que por kp y luego name: reordenarlas daría la
     * misma clave a dos búsquedas que devuelven resultados distintos.
     */
    private JsonNode canonicalize(JsonNode node, boolean sortArrays) {
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            node.properties().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> result.set(entry.getKey(),
                            canonicalize(entry.getValue(),
                                    sortArrays || ORDER_INSENSITIVE_NODE.equals(entry.getKey()))));
            return result;
        }

        if (node.isArray()) {
            List<JsonNode> children = new ArrayList<>();
            node.forEach(child -> children.add(canonicalize(child, sortArrays)));

            if (sortArrays) {
                children.sort(Comparator.comparing(JsonNode::toString));
            }

            ArrayNode result = objectMapper.createArrayNode();
            children.forEach(result::add);
            return result;
        }

        return node;
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(canonicalize(objectMapper.valueToTree(value), false));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();

            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
