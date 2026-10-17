package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.TrackAccessPointBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.infrastructure.TrackAccessPoint;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointKind;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointSide;
import com.alejandro.mtoconfiguration.mapper.infraestructure.TrackAccessPointMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackAccessPointDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.TrackAccessPointFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackAccessPointCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackAccessPointRepository;
import com.alejandro.mtoconfiguration.validator.infrastructure.TrackAccessPointValidator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La lista de puntos de acceso ({@code POST /filter}) como {@link Specification}, y los de una vía.
 *
 * <p>La estación es opcional: va con join externo, como la vía del aislador, y con uno interno un
 * acceso sin estación desaparecería de la búsqueda por texto. Que la consulta de verdad lo devuelva,
 * también ordenada por la estación, lo comprueba {@code FilterOptionalReferencesIT} contra PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackAccessPointServiceTest {

    @Mock
    private TrackAccessPointRepository repository;
    @Mock
    private TrackAccessPointMapper mapper;
    @Mock
    private TrackAccessPointValidator validator;
    @Mock
    private TrackAccessPointBusiness business;
    @Mock
    private TrackAccessPointCriteriaSearchRepository criteriaSearchRepository;

    @Mock
    private Root<TrackAccessPoint> root;
    @Mock
    private CriteriaQuery<Object> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Join<TrackAccessPoint, Track> track;
    @Mock
    private Join<TrackAccessPoint, Station> station;
    @Mock
    private Path<Object> trackId;
    @Mock
    private Path<Object> kind;
    @Mock
    private Path<Object> side;
    @Mock
    private Path<Object> roadRailAccess;
    @Mock
    private Path<Object> enabled;
    @Captor
    private ArgumentCaptor<Specification<TrackAccessPoint>> specification;

    private TrackAccessPointService service;

    @BeforeEach
    void setUp() {
        service = new TrackAccessPointService(repository, mapper, validator, business, criteriaSearchRepository);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        doReturn(track).when(root).join("track", JoinType.LEFT);
        doReturn(station).when(root).join("station", JoinType.LEFT);
        doReturn(trackId).when(track).get("id");
        doReturn(kind).when(root).get("kind");
        doReturn(side).when(root).get("side");
        doReturn(roadRailAccess).when(root).get("roadRailAccess");
        doReturn(enabled).when(root).get("enabled");
        when(cb.like(any(), anyString())).thenReturn(mock(Predicate.class));
    }

    /** Pide la lista con el filtro y aplica la {@link Specification} que llega al repositorio. */
    private void listWith(TrackAccessPointFilter filter) {
        service.getTrackAccessPoints(PageRequest.of(0, 20), filter);
        verify(repository).findAll(specification.capture(), any(Pageable.class));
        specification.getValue().toPredicate(root, query, cb);
    }

    @Nested
    @DisplayName("La lista filtrada")
    class Filtro {

        @Test
        @DisplayName("sin filtros no filtra por nada")
        void ausentesNoFiltran() {
            listWith(new TrackAccessPointFilter(null, null, null, null, null, null, null, null, null));

            verify(cb, never()).equal(any(Expression.class), any(Object.class));
            verify(cb, never()).like(any(), anyString());
        }

        @Test
        @DisplayName("la vía, la clase, el lado, los biviales y el estado filtran cuando vienen, también false")
        void presentesFiltran() {
            listWith(new TrackAccessPointFilter(null, 3L, null, null, TrackAccessPointKind.DEPOT,
                    TrackAccessPointSide.RIGHT, false, null, false));

            verify(cb).equal(trackId, 3L);
            verify(cb).equal(kind, TrackAccessPointKind.DEPOT);
            verify(cb).equal(side, TrackAccessPointSide.RIGHT);
            verify(cb).equal(roadRailAccess, false);
            verify(cb).equal(enabled, false);
        }

        @Test
        @DisplayName("la vía y la estación van con join externo, nunca interno")
        void joinsExternos() {
            listWith(new TrackAccessPointFilter("ACC", null, "VIA", "EST", null, null, null, "ACC", null));

            verify(root).join("track", JoinType.LEFT);
            verify(root).join("station", JoinType.LEFT);
            verify(root, never()).join(anyString());
            verify(root, never()).join(anyString(), eq(JoinType.INNER));
        }

        @Test
        @DisplayName("la búsqueda general mira el nombre, la vía y la estación, sin distinguir mayúsculas")
        void busquedaGeneral() {
            listWith(new TrackAccessPointFilter(null, null, null, null, null, null, null, "pn", null));

            verify(cb, times(3)).like(any(), eq("%PN%"));
        }
    }

    @Nested
    @DisplayName("Los de una vía")
    class DeUnaVia {

        @Test
        @DisplayName("se piden por KP y se devuelven mapeados")
        void porKp() {
            TrackAccessPoint entity = new TrackAccessPoint();
            TrackAccessPointDTO dto = new TrackAccessPointDTO();
            when(repository.findByTrackIdOrderByKpAscIdAsc(3L)).thenReturn(List.of(entity));
            when(mapper.toDTO(entity)).thenReturn(dto);

            assertThat(service.getTrackAccessPointsByTrackId(3L)).containsExactly(dto);
        }

        @Test
        @DisplayName("sin vía no se pregunta nada")
        void sinVia() {
            assertThat(service.getTrackAccessPointsByTrackId(null)).isEmpty();
            verifyNoInteractions(repository);
        }
    }

    @Test
    @DisplayName("no se cachea: lleva decimales, que la caché no lee")
    void noSeCachea() {
        assertThat(service.isCacheable()).isFalse();
    }
}
