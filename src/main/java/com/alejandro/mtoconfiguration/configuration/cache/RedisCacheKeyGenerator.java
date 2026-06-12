package com.alejandro.mtoconfiguration.configuration.cache;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.stream.Collectors;

@Component("redisCacheKeyGenerator")
public class RedisCacheKeyGenerator implements KeyGenerator {

    private final ObjectMapper objectMapper;

    public RedisCacheKeyGenerator(@Qualifier("redisCacheKeyObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    @Override
    public Object generate(Object target, Method method, @Nullable Object... params) {
        String serviceName = getServiceName(target);
        String methodName = method.getName();
        String paramsKey = buildParamsKey(params);

        if (paramsKey.isBlank()) {
            return serviceName + ":" + methodName;
        }

        return serviceName + ":" + methodName + ":" + paramsKey;
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

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
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
