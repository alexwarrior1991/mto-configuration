package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheConfig;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.SerializationException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fija que valores se pueden guardar y volver a leer de la cache.
 * <p>
 * El serializador usa default typing, que solo escribe el marcador @class de las
 * clases NO finales. Las listas inmutables del JDK (List.of, Stream.toList) son
 * finales: se escriben sin marcador y al releerlas Jackson falla. Escribir
 * funciona, leer no, asi que el fallo aparece en el primer acierto de cache y no
 * cuando se guarda. Jackson 3 no ofrece ningun DefaultTyping que cubra las clases
 * finales concretas, de modo que la unica salida es no devolverlas desde un metodo
 * cacheado.
 * <p>
 * Sin este test la restriccion es invisible: un .toList() en cualquier metodo
 * cacheado se convierte en un 500 en produccion la segunda vez que se llama.
 */
class RedisCacheValueSerializationTest {

    private RedisSerializationContext.SerializationPair<Object> pair;

    @BeforeEach
    void setUp() {
        RedisCacheProperties properties = new RedisCacheProperties();
        properties.setAllowedSubtypes(List.of(
                "com.alejandro.mtoconfiguration", "java.util", "java.time",
                "org.springframework.data.domain"));

        // Se toma el serializador REAL de la configuracion: si alguien lo cambia,
        // este test se entera.
        pair = new RedisCacheConfig()
                .redisCacheConfiguration(properties, "mto-configuration")
                .getValueSerializationPair();
    }

    @Test
    void shouldRoundTripAMutableList() {
        List<TestValue> original = new ArrayList<>(List.of(new TestValue("A", "uno")));

        assertThatCode(() -> assertThat(roundTrip(original)).isEqualTo(original))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldFailOnImmutableListsSoNoCachedMethodEverReturnsOne() {
        assertThatThrownBy(() -> roundTrip(List.of(new TestValue("A", "uno"))))
                .isInstanceOf(SerializationException.class);

        assertThatThrownBy(() -> roundTrip(Stream.of(new TestValue("A", "uno")).toList()))
                .isInstanceOf(SerializationException.class);
    }

    private Object roundTrip(Object value) {
        return pair.read(pair.write(value));
    }

    record TestValue(String code, String name) {
    }
}
