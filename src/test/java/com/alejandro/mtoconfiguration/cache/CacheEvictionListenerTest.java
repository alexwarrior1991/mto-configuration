package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.CacheEvictionEvent;
import com.alejandro.mtoconfiguration.configuration.cache.CacheEvictionListener;
import com.alejandro.mtoconfiguration.configuration.cache.LovCacheEvictionEvent;
import com.alejandro.mtoconfiguration.configuration.cache.LovCacheEvictionListener;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheEvictService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Lo que cada evento de invalidacion barre, y cuando.
 *
 * <p>El esquema de via ({@code TrackSchematicService}) depende de los ocho maestros de
 * infraestructura y de sus catalogos, pero el evento solo trae el nombre del servicio que
 * escribio: por eso los dos listeners lo vacian siempre, ademas de lo suyo. Si alguien quita esa
 * llamada, el esquema serviria una via vieja hasta seis horas y ningun otro test lo veria.
 */
@ExtendWith(MockitoExtension.class)
class CacheEvictionListenerTest {

    @Mock
    private RedisCacheEvictService evictService;

    @Test
    @DisplayName("una escritura de un maestro vacia sus cachés y, siempre, el esquema de via")
    void unaEscrituraVaciaLoSuyoYElEsquema() {
        new CacheEvictionListener(evictService).onCacheEviction(new CacheEvictionEvent("ProfileService"));

        InOrder enOrden = inOrder(evictService);
        enOrden.verify(evictService).evictNormalServiceCaches("ProfileService");
        enOrden.verify(evictService).evictTrackSchematics();
        verifyNoMoreInteractions(evictService);
    }

    @Test
    @DisplayName("un cambio de catalogo vacia ese LOV y tambien el esquema, que lleva sus codigos")
    void unCatalogoVaciaElLovYElEsquema() {
        new LovCacheEvictionListener(evictService).onLovCacheEviction(new LovCacheEvictionEvent("PoleType"));

        verify(evictService).evictLovCaches("PoleType");
        verify(evictService).evictTrackSchematics();
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
}
