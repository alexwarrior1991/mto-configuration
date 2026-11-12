package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.CacheEvictionEvent;
import com.alejandro.mtoconfiguration.configuration.cache.CacheEvictionListener;
import com.alejandro.mtoconfiguration.configuration.cache.LovCacheEvictionEvent;
import com.alejandro.mtoconfiguration.configuration.cache.LovCacheEvictionListener;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheEvictService;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.service.commons.BaseService;
import com.alejandro.mtoconfiguration.service.infraestructure.DisconnectorService;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileService;
import com.alejandro.mtoconfiguration.service.infraestructure.SteadyArmService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Lo que cada evento de invalidacion barre, y cuando.
 *
 * <p>El evento solo trae el nombre del servicio que escribio, y hay caches que dependen de
 * escrituras ajenas: el esquema de via ({@code TrackSchematicService}) de los ocho maestros y de
 * sus catalogos, y las dos caches de entidad ({@code DisconnectorService}, {@code SteadyArmService})
 * de los servicios que copian sus datos o escriben sus filas anidadas
 * ({@code CacheEvictionListener.DEPENDENT_SERVICES}). Si alguien quita una de esas llamadas, la
 * cache serviria una fila vieja hasta seis horas y ningun otro test lo veria.
 */
@ExtendWith(MockitoExtension.class)
class CacheEvictionListenerTest {

    @Mock
    private RedisCacheEvictService evictService;

    private CacheEvictionListener listener() {
        return new CacheEvictionListener(evictService);
    }

    /** El listener de catalogos con los servicios que habria en el contexto. */
    private LovCacheEvictionListener lovListener(BaseService<?, ?>... servicios) {
        @SuppressWarnings("unchecked")
        ObjectProvider<BaseService<?, ?>> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenAnswer(invocation -> Stream.of(servicios));
        return new LovCacheEvictionListener(evictService, provider);
    }

    /** Un servicio sin sus dependencias: de el solo se usa lo que declara, isCacheable(). */
    private static <S extends BaseService<?, ?>> S servicio(Class<S> type) {
        return mock(type, CALLS_REAL_METHODS);
    }

    @Test
    @DisplayName("una escritura de un maestro vacia sus cachés y el esquema de via, en una sola llamada")
    void unaEscrituraVaciaLoSuyoYElEsquema() {
        listener().onCacheEviction(new CacheEvictionEvent("SectionInsulatorService"));

        // Una llamada = una conexion a Redis; el esquema va dentro (evictAfterInfrastructureWrite).
        verify(evictService).evictAfterInfrastructureWrite(List.of("SectionInsulatorService"));
        verifyNoMoreInteractions(evictService);
    }

    @Nested
    @DisplayName("Un cambio de catalogo")
    class Catalogos {

        @Test
        @DisplayName("vacia ese LOV, los servicios cacheables (su DTO embebe la entrada) y el esquema, en una sola llamada")
        void unCatalogoVaciaElLovLosCacheablesYElEsquema() {
            lovListener(servicio(DisconnectorService.class), servicio(ProfileService.class),
                    servicio(SteadyArmService.class))
                    .onLovCacheEviction(new LovCacheEvictionEvent("DisconnectorFunction"));

            // Sin los dos servicios, un seccionador leido tras corregir la descripcion de su
            // funcion seguia saliendo con la vieja hasta seis horas (normal:item).
            verify(evictService).evictAfterLovWrite("DisconnectorFunction",
                    List.of("DisconnectorService", "SteadyArmService"));
            verifyNoMoreInteractions(evictService);
        }

        @Test
        @DisplayName("sin ningun servicio cacheable solo vacia el LOV y el esquema")
        void sinCacheablesSoloElLovYElEsquema() {
            lovListener(servicio(ProfileService.class))
                    .onLovCacheEviction(new LovCacheEvictionEvent("PoleType"));

            verify(evictService).evictAfterLovWrite("PoleType", List.of());
            verifyNoMoreInteractions(evictService);
        }

        @Test
        @DisplayName("guardian: todo servicio que se declara cacheable se vacia con cualquier catalogo")
        void todoServicioCacheableSeVaciaConUnCatalogo() {
            // Los servicios de verdad, tal como responden a isCacheable(). Uno nuevo que pase a
            // cachearse entra aqui solo; si el listener dejara de preguntarselo a los servicios y
            // volviera a una lista escrita a mano, este test es el que lo dice.
            List<BaseService<?, ?>> servicios = serviciosDeInfraestructura();
            List<String> cacheables = servicios.stream()
                    .filter(BaseService::isCacheable)
                    .map(servicio -> servicio.getClass().getSimpleName())
                    .sorted()
                    .toList();

            lovListener(servicios.toArray(BaseService[]::new))
                    .onLovCacheEviction(new LovCacheEvictionEvent("SteadyArmType"));

            assertThat(cacheables)
                    .as("los dos DTO cacheables embeben una entrada de catalogo")
                    .contains("DisconnectorService", "SteadyArmService");
            verify(evictService).evictAfterLovWrite("SteadyArmType", cacheables);
        }

        @Test
        @DisplayName("guardian: los DTO cacheables que embeben un catalogo son de servicios que se vacian")
        void losDtoCacheablesConCatalogoSonDeServiciosCacheables() {
            // La otra mitad: que la regla "se vacian los cacheables" cubre de verdad a quien embebe
            // un catalogo. Un DTO con una entrada de catalogo cuyo servicio no se declarase
            // cacheable pero se cacheara por otro camino no estaria en la lista.
            List<BaseService<?, ?>> servicios = serviciosDeInfraestructura();

            assertThat(servicios)
                    .filteredOn(BaseService::isCacheable)
                    .extracting(servicio -> servicio.getClass().getSimpleName())
                    .containsExactlyInAnyOrder("DisconnectorService", "SteadyArmService");
            assertThat(embebeCatalogo(DisconnectorDTO.class)).isTrue();
            assertThat(embebeCatalogo(SteadyArmDTO.class)).isTrue();
        }

        private boolean embebeCatalogo(Class<?> dto) {
            return java.util.Arrays.stream(dto.getDeclaredFields())
                    .anyMatch(field -> LovDTO.class.isAssignableFrom(field.getType()));
        }

        /** Todos los servicios concretos de infraestructura, sin contexto: solo importa isCacheable(). */
        @SuppressWarnings("unchecked")
        private List<BaseService<?, ?>> serviciosDeInfraestructura() {
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(BaseService.class));

            return scanner.findCandidateComponents("com.alejandro.mtoconfiguration.service.infraestructure")
                    .stream()
                    .map(definition -> {
                        try {
                            return (Class<? extends BaseService<?, ?>>) Class.forName(definition.getBeanClassName());
                        } catch (ClassNotFoundException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .<BaseService<?, ?>>map(CacheEvictionListenerTest::servicio)
                    .toList();
        }
    }

    @Test
    @DisplayName("los dos listeners esperan al commit: una transaccion deshecha no vacia nada")
    void escuchanTrasElCommit() throws NoSuchMethodException {
        TransactionalEventListener normal = CacheEvictionListener.class
                .getMethod("onCacheEviction", CacheEvictionEvent.class)
                .getAnnotation(TransactionalEventListener.class);
        TransactionalEventListener lov = LovCacheEvictionListener.class
                .getMethod("onLovCacheEviction", LovCacheEvictionEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(normal.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(lov.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Nested
    @DisplayName("Caches que dependen de lo que escribe otro servicio")
    class Dependientes {

        @Test
        @DisplayName("editar un perfil vacia los seccionadores (copian su codigo y su KP) y los brazos (van anidados)")
        void unPerfilArrastraSeccionadoresYBrazos() {
            listener().onCacheEviction(new CacheEvictionEvent("ProfileService"));

            // Primero quien escribio, luego a quien deja obsoleto: el orden es el de la lista.
            verify(evictService).evictAfterInfrastructureWrite(
                    List.of("ProfileService", "DisconnectorService", "SteadyArmService"));
            verifyNoMoreInteractions(evictService);
        }

        @ParameterizedTest(name = "{0} reconcilia hijos hasta el seccionador y el brazo")
        @ValueSource(strings = {"TrackService", "StationService", "ExecutionPackageService"})
        @DisplayName("los padres que reconcilian hijos anidados arrastran a los dos cacheables")
        void losPadresArrastranALosDosCacheables(String padre) {
            listener().onCacheEviction(new CacheEvictionEvent(padre));

            verify(evictService).evictAfterInfrastructureWrite(List.of(padre, "DisconnectorService", "SteadyArmService"));
            verifyNoMoreInteractions(evictService);
        }

        @Test
        @DisplayName("editar una mensula vacia los brazos (su 1:1) pero no los seccionadores")
        void unaMensulaArrastraSoloLosBrazos() {
            listener().onCacheEviction(new CacheEvictionEvent("CantileverService"));

            verify(evictService).evictAfterInfrastructureWrite(List.of("CantileverService", "SteadyArmService"));
            verifyNoMoreInteractions(evictService);
        }

        @Test
        @DisplayName("editar un seccionador no arrastra a nadie: nadie copia sus datos")
        void unSeccionadorNoArrastraANadie() {
            listener().onCacheEviction(new CacheEvictionEvent("DisconnectorService"));

            verify(evictService).evictAfterInfrastructureWrite(List.of("DisconnectorService"));
            verifyNoMoreInteractions(evictService);
        }

        @Test
        @DisplayName("todos los nombres del mapa son servicios que existen: un renombrado no lo deja mudo")
        void losNombresDelMapaExisten() {
            @SuppressWarnings("unchecked")
            Map<String, List<String>> dependencias = (Map<String, List<String>>)
                    ReflectionTestUtils.getField(CacheEvictionListener.class, "DEPENDENT_SERVICES");

            assertThat(dependencias).isNotEmpty();
            dependencias.forEach((escribe, obsoletos) -> {
                assertThatCode(() -> servicio(escribe)).as(escribe).doesNotThrowAnyException();
                assertThat(obsoletos).as("dependientes de " + escribe).isNotEmpty()
                        .allSatisfy(obsoleto -> assertThatCode(() -> servicio(obsoleto)).doesNotThrowAnyException());
            });
        }

        /** Los nombres de las claves son los simple names de las clases de servicio, como en RedisCacheKeyGenerator. */
        private static Class<?> servicio(String simpleName) throws ClassNotFoundException {
            return Class.forName("com.alejandro.mtoconfiguration.service.infraestructure." + simpleName);
        }
    }
}
