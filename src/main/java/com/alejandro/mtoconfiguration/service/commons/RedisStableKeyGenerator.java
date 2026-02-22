package com.alejandro.mtoconfiguration.service.commons;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component("redisStableKeyGenerator")
public class RedisStableKeyGenerator implements KeyGenerator {
    @Override
    public Object generate(Object target, Method method, @Nullable Object... params) {
        String owner = method.getDeclaringClass().getSimpleName();
        String signature = method.getName() + "(" +
                Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(",")) +
                ")";

        String normalizedArgs = normalizeParams(params);
        String argsHash = sha256Hex(normalizedArgs);

        return owner + "#" + signature + "#" + argsHash;
    }

    /**
     * Normalizes parameters into a delimited string
     */
    private String normalizeParams(@Nullable Object... params) {
        if (params == null || params.length == 0) return "<no-args>";

        return Arrays.stream(params)
                .map(this::normalizeValue)
                .collect(Collectors.joining("|"));
    }

    /**
     * Converts value to string for stable key generation
     */
    private String normalizeValue(@Nullable Object v) {
        if (v == null) return "<null>";

        if (v instanceof String s) {
            String t = s.trim();
            return t.isEmpty() ? "<empty>" : t;
        }

        if (v instanceof UUID u) return u.toString();

        if (v instanceof Enum<?> e) return e.name();

        // Normalizes array to comma‑separated string of normalized elements
        if (v.getClass().isArray()) {
            int len = Array.getLength(v);
            return IntStream.range(0, len)
                    .mapToObj(i -> normalizeValue(Array.get(v, i)))
                    .collect(Collectors.joining(",", "[", "]"));
        }

        if (v instanceof Collection<?> c) {
            return c.stream()
                    .map(this::normalizeValue)
                    .collect(Collectors.joining(",", "[", "]"));
        }

        if (v instanceof Map<?, ?> m) {
            // Normalizes map entries into sorted key‑value pairs
            return m.entrySet().stream()
                    .sorted(Comparator.comparing(e -> String.valueOf(e.getKey())))
                    .map(e -> normalizeValue(e.getKey()) + "=" + normalizeValue(e.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }

        return String.valueOf(v);
    }

    private String sha256Hex(String input) {
        // Computes SHA‑256 hash or returns fallback hash
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
