package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.CacheGeneration;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheAvailability;
import com.alejandro.mtoconfiguration.configuration.cache.ResilientCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El {@code put} que {@link ResilientCache} deshace: el de una lectura que fallo en la cache y
 * cargo mientras una escritura la vaciaba ({@link CacheGeneration}). Contra Redis de verdad, con el
 * {@code @Cacheable} y el vaciado reales, lo prueba {@code CacheGenerationIT}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResilientCacheTest {

    private static final String KEY = "ProfileStatusService:findAll";

    @Mock
    private Cache delegate;
    @Mock
    private RedisCacheAvailability availability;
    @Mock
    private CacheGeneration generation;

    private ResilientCache cache;

    @BeforeEach
    void setUp() {
        when(availability.isAvailable()).thenReturn(true);
        cache = new ResilientCache(delegate, availability, generation);
    }

    @Nested
    @DisplayName("Una lectura que falla y guarda")
    class FalloYPut {

        @Test
        @DisplayName("sin escrituras entre el fallo y el put, lo guardado se queda")
        void sinEscriturasSeQueda() {
            when(generation.current()).thenReturn(7L);

            assertThat(cache.get(KEY)).isNull();
            cache.put(KEY, "lista");

            verify(delegate).put(KEY, "lista");
            verify(delegate, never()).evict(any());
        }

        @Test
        @DisplayName("si una escritura vacio la cache mientras se cargaba, lo guardado se borra")
        void conUnaEscrituraEnMedioSeBorra() {
            when(generation.current()).thenReturn(7L, 8L);

            assertThat(cache.get(KEY)).isNull();
            cache.put(KEY, "lista vieja");

            // Primero guarda y luego mira: si mirase antes, la escritura podria colarse entre
            // la mirada y el put, y su DEL llegaria antes que el put.
            InOrder orden = inOrder(delegate, generation);
            orden.verify(generation).current();
            orden.verify(delegate).put(KEY, "lista vieja");
            orden.verify(generation).current();
            orden.verify(delegate).evict(KEY);
        }

        @Test
        @DisplayName("la generacion se apunta tambien al fallar con el tipo")
        void tambienConElTipo() {
            when(generation.current()).thenReturn(7L, 8L);

            assertThat(cache.get(KEY, String.class)).isNull();
            cache.put(KEY, "lista vieja");

            verify(delegate).evict(KEY);
        }

        @Test
        @DisplayName("un acierto no lee la generacion")
        void unAciertoNoLaLee() {
            when(delegate.get(KEY)).thenReturn(new SimpleValueWrapper("lista"));

            assertThat(cache.get(KEY).get()).isEqualTo("lista");

            verify(generation, never()).current();
        }

        @Test
        @DisplayName("cada fallo se compara con el suyo: el put de una clave no deshace el de otra")
        void cadaClaveConElSuyo() {
            when(generation.current()).thenReturn(7L, 8L, 8L, 8L);

            cache.get("A");
            cache.get("B");
            cache.put("B", "b");
            cache.put("A", "a");

            verify(delegate, never()).evict("B");
            verify(delegate).evict("A");
        }

        @Test
        @DisplayName("el fallo de un hilo no cuenta para el put de otro")
        void porHilo() throws Exception {
            when(generation.current()).thenReturn(7L, 8L);
            cache.get(KEY);

            // En otro hilo, un put sin fallo antes en ese hilo: guarda sin mas, como antes.
            CompletableFuture.runAsync(() -> cache.put(KEY, "de otro hilo"), tarea -> Thread.ofVirtual().start(tarea))
                    .get(5, TimeUnit.SECONDS);

            verify(delegate).put(KEY, "de otro hilo");
            verify(delegate, never()).evict(any());
        }
    }

    @Nested
    @DisplayName("Lo que guarda sin mas, como antes")
    class ComoAntes {

        @Test
        @DisplayName("un put sin un fallo antes en el hilo no lee la generacion")
        void putSinFallo() {
            cache.put(KEY, "lista");

            verify(delegate).put(KEY, "lista");
            verify(generation, never()).current();
        }

        @Test
        @DisplayName("el fallo se olvida con su put: un segundo put de la clave guarda sin mas")
        void seOlvidaConSuPut() {
            when(generation.current()).thenReturn(7L, 7L, 8L);

            cache.get(KEY);
            cache.put(KEY, "uno");
            cache.put(KEY, "dos");

            verify(delegate, times(2)).put(any(), any());
            verify(generation, times(2)).current();
            verify(delegate, never()).evict(any());
        }
    }

    @Nested
    @DisplayName("Con Redis degradado o fallando")
    class Degradado {

        @Test
        @DisplayName("con el cortocircuito abierto ni lee la generacion ni guarda")
        void cortocircuito() {
            when(availability.isAvailable()).thenReturn(false);

            assertThat(cache.get(KEY)).isNull();
            cache.put(KEY, "lista");

            verify(generation, never()).current();
            verify(delegate, never()).put(any(), any());
        }

        @Test
        @DisplayName("el fallo de una lectura degradada no se queda esperando al put de cuando vuelva")
        void elPutTrasVolverNoUsaUnFalloViejo() {
            when(generation.current()).thenReturn(7L, 8L);
            cache.get(KEY);
            when(availability.isAvailable()).thenReturn(false);
            cache.put(KEY, "durante el corte");

            // Redis vuelve: el put siguiente ya no tiene fallo apuntado.
            when(availability.isAvailable()).thenReturn(true);
            cache.put(KEY, "despues");

            verify(delegate).put(KEY, "despues");
            verify(delegate, never()).evict(any());
        }

        @Test
        @DisplayName("si leer la generacion falla al fallar la lectura, la excepcion llega al manejador de Spring")
        void laGeneracionFallaEnElGet() {
            when(generation.current()).thenThrow(new RedisConnectionFailureException("Connection reset"));

            // ResilientCacheErrorHandler la trata como un fallo de caché y arma el cortocircuito.
            assertThatThrownBy(() -> cache.get(KEY)).isInstanceOf(RedisConnectionFailureException.class);
        }
    }
}
