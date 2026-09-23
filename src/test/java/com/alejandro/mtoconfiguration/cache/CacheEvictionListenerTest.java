package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.CacheEvictionEvent;
import com.alejandro.mtoconfiguration.configuration.cache.CacheEvictionListener;
import com.alejandro.mtoconfiguration.configuration.cache.LovCacheEvictionEvent;
import com.alejandro.mtoconfiguration.configuration.cache.LovCacheEvictionListener;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheEvictService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
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

    @Test
    @DisplayName("una escritura de un maestro vacia sus cachés y el esquema de via, en una sola llamada")
    void unaEscrituraVaciaLoSuyoYElEsquema() {
        listener().onCacheEviction(new CacheEvictionEvent("SectionInsulatorService"));

        // Una llamada = una conexion a Redis; el esquema va dentro (evictAfterInfrastructureWrite).
        verify(evictService).evictAfterInfrastructureWrite(List.of("SectionInsulatorService"));
        verifyNoMoreInteractions(evictService);
    }

    @Test
    @DisplayName("un cambio de catalogo vacia ese LOV y el esquema, que lleva sus codigos, en una sola llamada")
    void unCatalogoVaciaElLovYElEsquema() {
        new LovCacheEvictionListener(evictService).onLovCacheEviction(new LovCacheEvictionEvent("PoleType"));

        verify(evictService).evictAfterLovWrite("PoleType");
        verifyNoMoreInteractions(evictService);
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
