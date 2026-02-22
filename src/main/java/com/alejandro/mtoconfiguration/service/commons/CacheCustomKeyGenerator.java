package com.alejandro.mtoconfiguration.service.commons;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class CacheCustomKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, @Nullable Object... params) {
        return method.getName() + "#" + method.getReturnType().getSimpleName() + "#" +
                Arrays.stream(params)
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .collect(Collectors.joining("-"));
    }
}
