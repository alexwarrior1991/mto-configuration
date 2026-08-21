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
 */
class CacheableServicesTest {

    private static final String ENTITY_DTO_PACKAGE =
            "com.alejandro.mtoconfiguration.model.synchronous.infrastructure";

    /** Servicios que declaran isCacheable() = true. Debe coincidir con los DTO sin hijos. */
    private static final Set<String> DECLARADOS_CACHEABLES =
            Set.of("SteadyArmDTO", "SectionInsulatorDTO", "DisconnectorDTO");

    private static final Set<String> DTOS = Set.of(
            "ExecutionPackageDTO", "StationDTO", "TrackDTO", "ProfileDTO",
            "CantileverDTO", "SteadyArmDTO", "SectionInsulatorDTO", "DisconnectorDTO");

    @Test
    void soloDebenCachearseLosDtoQueNoEmbebenOtrasEntidades() throws Exception {
        Set<String> sinHijos = new TreeSet<>();

        for (String dto : DTOS) {
            Class<?> type = Class.forName(ENTITY_DTO_PACKAGE + "." + dto);
            if (!embebeOtraEntidad(type)) {
                sinHijos.add(dto);
            }
        }

        assertThat(sinHijos)
                .as("""
                        La composicion de los DTO ya no cuadra con isCacheable().
                        Si un DTO ha dejado de embeber hijos, su servicio puede pasar a \
                        isCacheable() = true. Si ha empezado a embeberlos, hay que ponerlo \
                        a false o servira datos obsoletos. Ver BaseService.isCacheable().""")
                .containsExactlyInAnyOrderElementsOf(DECLARADOS_CACHEABLES);
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
