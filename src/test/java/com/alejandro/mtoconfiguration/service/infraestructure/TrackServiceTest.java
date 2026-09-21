package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.TrackBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.infraestructure.TrackMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.TrackFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackRepository;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.utils.InfrastructureUtils;
import com.alejandro.mtoconfiguration.validator.infrastructure.TrackValidator;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo propio de {@link TrackService}: como se traduce el filtro funcional a un predicado y con que
 * fila se responde.
 *
 * <p>El booleano {@code enabled} es el que se rompia en silencio: era primitivo, asi que un filtro
 * sin el campo valia {@code false} y {@code POST /tracks/filter} con {@code {}} devolvia solo las
 * vias desactivadas. Y la fila de una lista va sin perfiles: una pagina de vias con sus miles de
 * perfiles no es una lista.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackServiceTest {

    @Mock
    private TrackRepository repository;
    @Mock
    private TrackMapper mapper;
    @Mock
    private TrackValidator validator;
    @Mock
    private TrackBusiness business;
    @Mock
    private TrackCriteriaSearchRepository criteriaSearchRepository;
    @Mock
    private MasterDataService masterDataService;
    @Mock
    private InfrastructureUtils infrastructureUtils;

    private TrackService service;

    @BeforeEach
    void setUp() {
        service = new TrackService(repository, mapper, validator, business, criteriaSearchRepository,
                masterDataService, infrastructureUtils);
        when(repository.findAll(any(Predicate.class), any(Pageable.class))).thenReturn(Page.empty());
    }

    private static TrackFilter filter(Boolean enabled) {
        return new TrackFilter(null, null, null, null, enabled);
    }

    private BooleanBuilder predicateFor(Boolean enabled) {
        service.getTracks(PageRequest.of(0, 20), filter(enabled));
        ArgumentCaptor<Predicate> captor = ArgumentCaptor.forClass(Predicate.class);
        verify(repository).findAll(captor.capture(), any(Pageable.class));
        return (BooleanBuilder) captor.getValue();
    }

    @Nested
    @DisplayName("Filtro por estado")
    class FiltroPorEstado {

        @Test
        @DisplayName("sin estado no filtra: activas e inactivas a la vez")
        void ausenteNoFiltra() {
            assertThat(predicateFor(null).hasValue()).isFalse();
        }

        @Test
        @DisplayName("true devuelve solo las activas")
        void activas() {
            assertThat(predicateFor(true).toString()).contains("track.enabled = true");
        }

        @Test
        @DisplayName("false devuelve solo las desactivadas")
        void desactivadas() {
            assertThat(predicateFor(false).toString()).contains("track.enabled = false");
        }
    }

    @Test
    @DisplayName("la lista filtrada responde con la fila resumida, sin perfiles")
    void laListaVaSinPerfiles() {
        Page<Track> page = new PageImpl<>(List.of(new Track()));
        when(repository.findAll(any(Predicate.class), any(Pageable.class))).thenReturn(page);

        service.getTracks(PageRequest.of(0, 20), filter(null));

        verify(mapper).mapToSummaryDTOs(page);
        verify(mapper, never()).toDTO(any());
    }
}
