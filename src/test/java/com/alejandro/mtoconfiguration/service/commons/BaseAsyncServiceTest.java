package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.service.commons.CrudServiceFixtures.TestDTO;
import com.alejandro.mtoconfiguration.service.commons.CrudServiceFixtures.TestEntity;
import com.alejandro.mtoconfiguration.service.commons.CrudServiceFixtures.TestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Fachada asincrona: cada metodo debe delegar en el servicio sincrono y envolver su resultado.
 *
 * <p>Aqui se llama directamente al bean, sin el proxy de {@code @Async}, porque lo que interesa es
 * la delegacion y no el hilo en que corre: si el metodo no delega bien, tampoco lo hara en el pool.
 * El caso que si tiene logica propia es {@code fetchAndProcessAsync}, que traga las excepciones de
 * cada elemento para que un id malo no tumbe el lote entero.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BaseAsyncServiceTest {

    @Mock
    private TestService delegate;

    private BaseAsyncService<TestDTO, TestEntity, TestService> asyncService;

    @BeforeEach
    void setUp() {
        asyncService = new BaseAsyncService<>() {
            @Override
            protected TestService getService() {
                return delegate;
            }
        };
    }

    @Test
    @DisplayName("cada operacion delega en el servicio sincrono")
    void delegacion() {
        TestDTO dto = new TestDTO(1L);
        List<TestDTO> lista = List.of(dto);
        SearchRequestDTO request = new SearchRequestDTO();

        when(delegate.getById(1L)).thenReturn(dto);
        when(delegate.create(dto)).thenReturn(dto);
        when(delegate.update(dto)).thenReturn(dto);
        when(delegate.bulkCreate(lista)).thenReturn(lista);
        when(delegate.bulkUpdate(lista)).thenReturn(lista);
        when(delegate.findAll()).thenReturn(lista);
        when(delegate.findAll(PageRequest.of(0, 20))).thenReturn(new PageImpl<>(lista));
        when(delegate.search(request)).thenReturn(new PageImpl<>(lista));

        assertThat(asyncService.getByIdAsync(1L)).isCompletedWithValue(dto);
        assertThat(asyncService.createAsync(dto)).isCompletedWithValue(dto);
        assertThat(asyncService.updateAsync(dto)).isCompletedWithValue(dto);
        assertThat(asyncService.bulkCreateAsync(lista)).isCompletedWithValue(lista);
        assertThat(asyncService.bulkUpdateAsync(lista)).isCompletedWithValue(lista);
        assertThat(asyncService.findAllAsync()).isCompletedWithValue(lista);
        assertThat(asyncService.findAllAsync(PageRequest.of(0, 20)).join().getContent()).isEqualTo(lista);
        assertThat(asyncService.searchAsync(request).join().getContent()).isEqualTo(lista);
    }

    @Test
    @DisplayName("fetchAndProcessAsync sin ids no consulta el servicio")
    void fetchSinIds() {
        assertThat(asyncService.fetchAndProcessAsync(null, UnaryOperator.identity())).isCompletedWithValue(List.of());
        assertThat(asyncService.fetchAndProcessAsync(List.of(), UnaryOperator.identity())).isCompletedWithValue(List.of());

        verifyNoInteractions(delegate);
    }

    @Test
    @DisplayName("fetchAndProcessAsync aplica el procesador a cada elemento")
    void fetchAplicaProcesador() {
        TestDTO first = new TestDTO(1L);
        TestDTO second = new TestDTO(2L);
        when(delegate.getById(1L)).thenReturn(first);
        when(delegate.getById(2L)).thenReturn(second);

        List<TestDTO> result = asyncService.fetchAndProcessAsync(List.of(1L, 2L), dto -> {
            dto.setId(dto.getId() * 10);
            return dto;
        }).join();

        assertThat(result).extracting(TestDTO::getId).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("un id que falla se descarta y el resto del lote continua")
    void fetchToleraFallos() {
        // Es el motivo de existir de processSafely: en una carga de cientos de ids, uno que ya no
        // existe no puede tumbar los demas.
        TestDTO ok = new TestDTO(2L);
        when(delegate.getById(1L)).thenThrow(new BaseException("no existe"));
        when(delegate.getById(2L)).thenReturn(ok);

        List<TestDTO> result = asyncService.fetchAndProcessAsync(List.of(1L, 2L), UnaryOperator.identity()).join();

        assertThat(result).containsExactly(ok);
        verify(delegate).getById(2L);
    }

    @Test
    @DisplayName("un procesador que devuelve nulo no mete huecos en el resultado")
    void fetchDescartaNulos() {
        when(delegate.getById(1L)).thenReturn(new TestDTO(1L));

        List<TestDTO> result = asyncService.fetchAndProcessAsync(List.of(1L), dto -> null).join();

        assertThat(result).isEmpty();
    }
}
