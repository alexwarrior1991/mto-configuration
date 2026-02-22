package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

@Component
public class CacheCustomKeyDTOGenerator implements KeyGenerator {
    @Override
    public Object generate(Object target, Method method, @Nullable Object... params) {
        LovDTO lov = Arrays.stream(params)
                .filter(Objects::nonNull)
                .filter(LovDTO.class::isInstance)
                .map(LovDTO.class::cast)
                .findFirst().orElse(null);

        String paramKey = getParamKey(lov, params);

        return method.getName() + "#" + paramKey;
    }

    private String getParamKey(LovDTO lov, Object... params) {
        if (lov == null) {
            return "";
        }

        if (lov.getId() != null) {
            return getFunctionName(params[1]) + "#" + lov.getId().toString();
        }

        return getFunctionName(params[2]) + "#" + lov.getCode();
    }

    /**
     * Extracts function name from serialized lambda if possible
     */
    private String getFunctionName(Object function) {
        try {
            Method writeReplace = function.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object serializedLambda = writeReplace.invoke(function);

            if (serializedLambda instanceof SerializedLambda lambda) {
                return lambda.getImplMethodName();
            }
        } catch (Exception e) {
        }

        return function.getClass().getSimpleName();
    }
}
