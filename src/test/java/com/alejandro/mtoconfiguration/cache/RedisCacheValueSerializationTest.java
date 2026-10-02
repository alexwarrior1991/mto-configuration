package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheConfig;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.SerializationException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.schematic.TrackSchematicDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.FoundationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.FoundationTypeDTO;

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

    /**
     * El esquema de via es el unico DTO con hijos que se cachea: records anidados, listas
     * mutables, Long, Integer, Boolean y texto en vez de BigDecimal. Si alguien le mete un
     * BigDecimal o un List.of(), es aqui donde se ve, no en el primer acierto en produccion.
     */
    @Test
    void shouldRoundTripATrackSchematicWithItsNestedRecords() {
        var arm = new TrackSchematicDTO.CantileverArm(21L, "PT1", "-200", "5.300", "1.400", "SA1", 1200L);
        var disconnector = new TrackSchematicDTO.DisconnectorMark(40L, "SEC-40", true, "FEED", "ATOCHA");
        var profile = new TrackSchematicDTO.ProfileNode(7L, "P-007", "12.345", 1, "55.000", "HEB", null, "OK",
                "-2500", new ArrayList<>(List.of("S1")), new ArrayList<>(List.of(arm)), disconnector);
        var bare = new TrackSchematicDTO.ProfileNode(8L, "P-008", "70.000", 2, null, null, null, null,
                null, new ArrayList<>(), new ArrayList<>(), null);
        var turnout = new TrackSchematicDTO.SwitchMark(60L, "W31", "15.500", 9, "VIA 1");
        var insulator = new TrackSchematicDTO.InsulatorMark(50L, "AIS-50", "15.000", "TRACK_CONNECTION", true,
                "ATOCHA", "VIA 1", "VIA 2", new ArrayList<>(List.of(turnout)));
        var original = new TrackSchematicDTO(3L, "VIA 1", true, "EP4", new ArrayList<>(List.of("ATOCHA")),
                new ArrayList<>(List.of(profile, bare)), new ArrayList<>(List.of(insulator)));

        assertThat(roundTrip(original)).isEqualTo(original);
    }

    /**
     * Un catalogo se cachea entero ({@code findAll}, {@code findById}) y el serializador respeta las
     * anotaciones de Jackson del DTO: lo que la API oculta tampoco se guarda. La version y quien la
     * toco por ultima vez tienen que sobrevivir a la cache, o un acierto devolveria la entrada sin
     * ellas y el bloqueo optimista dejaria de comprobar nada.
     */
    @Test
    void shouldRoundTripACatalogueWithItsVersionAndItsParentType() {
        FoundationTypeDTO type = new FoundationTypeDTO();
        type.setId(4L);
        type.setCode("FT1");
        type.setVersionNumber(2);
        FoundationDTO foundation = new FoundationDTO();
        foundation.setId(9L);
        foundation.setCode("F1");
        foundation.setDescription("Zapata");
        foundation.setEnabled(true);
        foundation.setDrawingNumber(1234L);
        foundation.setFoundationType(type);
        foundation.setVersionNumber(3);
        foundation.setVersionDate(LocalDateTime.of(2026, 9, 30, 8, 15));
        foundation.setVersionUser("ana");

        Object read = roundTrip(new ArrayList<>(List.of(foundation)));

        assertThat(read).asInstanceOf(LIST).singleElement()
                .isInstanceOfSatisfying(FoundationDTO.class, cached -> {
                    assertThat(cached.getVersionNumber()).isEqualTo(3);
                    assertThat(cached.getVersionDate()).isEqualTo(LocalDateTime.of(2026, 9, 30, 8, 15));
                    assertThat(cached.getVersionUser()).isEqualTo("ana");
                    assertThat(cached.getDrawingNumber()).isEqualTo(1234L);
                    assertThat(cached.getFoundationType().getCode()).isEqualTo("FT1");
                    assertThat(cached.getFoundationType().getVersionNumber()).isEqualTo(2);
                });
    }

    private Object roundTrip(Object value) {
        return pair.read(pair.write(value));
    }

    record TestValue(String code, String name) {
    }
}
