package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vigila que la clasificacion de servicios cacheables siga cuadrando con lo que los
 * DTO declaran de verdad.
 * <p>
 * La regla: un DTO que embebe otro DTO de ENTIDAD no se puede cachear, porque la
 * invalidacion es por servicio y no por id, de modo que su entrada queda obsoleta en
 * cuanto cambia cualquier hijo de cualquier registro. Los LOV embebidos no cuentan:
 * se editan casi nunca.
 * <p>
 * Sin este test la clasificacion se pudre en silencio: alguien anade un campo a
 * SteadyArmDTO dentro de seis meses, nadie toca isCacheable(), y vuelven los datos
 * desactualizados sin que salte nada.
 * <p>
 * Y la otra regla de lo que se puede cachear (cache.md): lo que se guarda tiene que poder
 * leerse, y el serializador de la cache no lee BigDecimal. Un DTO sin hijos pero con decimales
 * tampoco es cacheable mientras los lleve como numero (TrackAccessPointDTO: el KP, las toneladas y
 * las coordenadas); el perfil y el seccionador los pasaron a texto para poder cachearse.
 * <p>
 * Lo que este test NO ve: un DTO cacheable que copia campos planos de otra entidad
 * (DisconnectorDTO.profileCode y profileKp salen del perfil) o cuya fila se escribe
 * anidada desde otro servicio (el seccionador dentro de un PUT de perfil o de
 * estacion, el brazo dentro de un PUT de mensula). Ahi la clasificacion sigue
 * cuadrando y aun asi la cache miente, porque el evento solo trae el nombre de quien
 * escribio. Esas dependencias viven en CacheEvictionListener.DEPENDENT_SERVICES y las
 * pina CacheEvictionListenerTest: un campo copiado nuevo es una entrada mas alli.
 */
class CacheableServicesTest {

    private static final String ENTITY_DTO_PACKAGE =
            "com.alejandro.mtoconfiguration.model.synchronous.infrastructure";

    /**
     * Servicios que declaran isCacheable() = true. Debe coincidir con los DTO sin hijos ni decimales.
     *
     * <p>{@code SectionInsulatorDTO} salió de aquí cuando el aislador pasó a llevar sus agujas:
     * desde entonces embebe hijos, y una entrada cacheada se quedaría mintiendo en cuanto cambiara
     * una aguja, que la caché no ve.
     */
    private static final Set<String> DECLARADOS_CACHEABLES =
            Set.of("SteadyArmDTO", "DisconnectorDTO");

    private static final Set<String> DTOS = Set.of(
            "ExecutionPackageDTO", "StationDTO", "TrackDTO", "ProfileDTO",
            "CantileverDTO", "SteadyArmDTO", "SectionInsulatorDTO", "DisconnectorDTO",
            "TrackAccessPointDTO");

    @Test
    void soloDebenCachearseLosDtoQueNoEmbebenOtrasEntidades() throws Exception {
        Set<String> cacheables = new TreeSet<>();

        for (String dto : DTOS) {
            Class<?> type = Class.forName(ENTITY_DTO_PACKAGE + "." + dto);
            if (!embebeOtraEntidad(type) && !llevaDecimales(type)) {
                cacheables.add(dto);
            }
        }

        assertThat(cacheables)
                .as("""
                        La composicion de los DTO ya no cuadra con isCacheable().
                        Si un DTO ha dejado de embeber hijos y no lleva decimales, su servicio puede \
                        pasar a isCacheable() = true. Si ha empezado a embeberlos, o a llevar un \
                        BigDecimal, hay que ponerlo a false: serviria datos obsoletos o no se podria \
                        leer de la cache. Ver BaseService.isCacheable() y cache.md.""")
                .containsExactlyInAnyOrderElementsOf(DECLARADOS_CACHEABLES);
    }

    /** La cache escribe un BigDecimal pero no lo lee (RedisCacheValueSerializationTest). */
    private boolean llevaDecimales(Class<?> dto) {
        return Arrays.stream(dto.getDeclaredFields())
                .flatMap(this::tiposDe)
                .anyMatch(java.math.BigDecimal.class::equals);
    }

    /** Recorre tambien los genericos, para que List<ProfileDTO> cuente como hijo. */
    private boolean embebeOtraEntidad(Class<?> dto) {
        return Arrays.stream(dto.getDeclaredFields())
                .flatMap(this::tiposDe)
                .anyMatch(t -> BaseDTO.class.isAssignableFrom(t)
                        && t.getName().startsWith(ENTITY_DTO_PACKAGE));
    }

    private java.util.stream.Stream<Class<?>> tiposDe(Field field) {
        java.util.List<Class<?>> tipos = new java.util.ArrayList<>();
        tipos.add(field.getType());

        if (field.getGenericType() instanceof java.lang.reflect.ParameterizedType pt) {
            Arrays.stream(pt.getActualTypeArguments())
                    .filter(Class.class::isInstance)
                    .map(Class.class::cast)
                    .forEach(tipos::add);
        }

        return tipos.stream();
    }
}
