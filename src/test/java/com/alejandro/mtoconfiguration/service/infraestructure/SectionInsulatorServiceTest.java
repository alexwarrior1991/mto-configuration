package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.SectionInsulatorBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulatorSwitch;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.enums.infrastructure.SectionInsulatorInstallationType;
import com.alejandro.mtoconfiguration.mapper.infraestructure.SectionInsulatorMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.SectionInsulatorFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.utils.InfrastructureUtils;
import com.alejandro.mtoconfiguration.validator.infrastructure.SectionInsulatorValidator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los filtros de la lista de aisladores ({@code POST /filter}), como {@link Specification}.
 *
 * <p>La estación y la vía se alcanzan con join externo: la vía es opcional, y con uno interno un
 * aislador sin vía desaparecía de la búsqueda por texto. Las agujas van con un {@code EXISTS}, nunca
 * con un join de la colección, que repetiría el aislador en la página. Que la consulta de verdad lo
 * devuelva, también ordenada por la vía, lo comprueba {@code FilterOptionalReferencesIT} contra
 * PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SectionInsulatorServiceTest {

    @Mock
    private SectionInsulatorRepository repository;
    @Mock
    private SectionInsulatorMapper mapper;
    @Mock
    private SectionInsulatorValidator validator;
    @Mock
    private SectionInsulatorBusiness business;
    @Mock
    private SectionInsulatorCriteriaSearchRepository criteriaSearchRepository;
    @Mock
    private MasterDataService masterDataService;
    @Mock
    private InfrastructureUtils infrastructureUtils;

    @Mock
    private Root<SectionInsulator> root;
    @Mock
    private CriteriaQuery<Object> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Join<SectionInsulator, Station> station;
    @Mock
    private Join<SectionInsulator, Track> track;
    @Mock
    private Path<Object> installationType;
    @Mock
    private Path<Object> enabled;
    @Mock(answer = Answers.RETURNS_SELF)
    private Subquery<Integer> anySwitch;
    @Mock
    private Root<SectionInsulator> correlated;
    @Mock
    private Join<SectionInsulator, SectionInsulatorSwitch> switches;
    @Captor
    private ArgumentCaptor<Specification<SectionInsulator>> specification;

    private SectionInsulatorService service;

    @BeforeEach
    void setUp() {
        service = new SectionInsulatorService(repository, mapper, validator, business, criteriaSearchRepository,
                masterDataService, infrastructureUtils);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        doReturn(station).when(root).join("station", JoinType.LEFT);
        doReturn(track).when(root).join("track", JoinType.LEFT);
        doReturn(installationType).when(root).get("installationType");
        doReturn(enabled).when(root).get("enabled");
        doReturn(anySwitch).when(query).subquery(Integer.class);
        doReturn(correlated).when(anySwitch).correlate(root);
        doReturn(switches).when(correlated).join("switches");
        when(cb.like(any(), anyString())).thenReturn(mock(Predicate.class));
        when(cb.exists(any())).thenReturn(mock(Predicate.class));
    }

    /** Pide la lista con el filtro y aplica la {@link Specification} que llega al repositorio. */
    private void listWith(SectionInsulatorFilter filter) {
        service.getSectionInsulators(PageRequest.of(0, 20), filter);
        verify(repository).findAll(specification.capture(), any(Pageable.class));
        specification.getValue().toPredicate(root, query, cb);
    }

    @Test
    @DisplayName("sin tipo de instalación ni estado no filtra por ellos")
    void ausentesNoFiltran() {
        listWith(new SectionInsulatorFilter(null, null, null, null, null, null, null));

        verify(cb, never()).equal(any(Expression.class), any(Object.class));
        verify(query, never()).subquery(any(Class.class));
    }

    @Test
    @DisplayName("el tipo de instalación y el estado filtran cuando vienen, también false")
    void presentesFiltran() {
        listWith(new SectionInsulatorFilter(null, null, null, null,
                SectionInsulatorInstallationType.IN_TRACK, null, false));

        verify(cb).equal(installationType, SectionInsulatorInstallationType.IN_TRACK);
        verify(cb).equal(enabled, false);
    }

    @Test
    @DisplayName("la estación y la vía van con join externo, nunca interno")
    void joinsExternos() {
        listWith(new SectionInsulatorFilter("SI", "HER", "VIA", "W31", null, "HER", null));

        verify(root).join("station", JoinType.LEFT);
        verify(root).join("track", JoinType.LEFT);
        verify(root, never()).join(anyString());
        verify(root, never()).join(anyString(), eq(JoinType.INNER));
    }

    @Test
    @DisplayName("las agujas van con un EXISTS, en su filtro y en la búsqueda general")
    void agujasConExists() {
        listWith(new SectionInsulatorFilter(null, null, null, "w3", null, "w3", null));

        verify(correlated, times(2)).join("switches");
        verify(cb, times(2)).exists(anySwitch);
        verify(root, never()).join(eq("switches"), any(JoinType.class));
        // La búsqueda general mira el nombre, la estación, la vía y las agujas; el filtro, las agujas.
        verify(cb, times(5)).like(any(), eq("%W3%"));
    }
}
