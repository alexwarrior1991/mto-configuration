package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.DisconnectorBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.lov.DisconnectorFunction;
import com.alejandro.mtoconfiguration.mapper.infraestructure.DisconnectorMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.DisconnectorFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.validator.infrastructure.DisconnectorValidator;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los filtros de la lista de seccionadores ({@code POST /filter}), como {@link Specification}.
 *
 * <p>{@code onLoad} era primitivo, asi que {@code false} y "no viene" eran lo mismo y README_API.md
 * llego a documentarlo como que {@code false} no filtraba. Ahora {@code true} y {@code false} filtran
 * por ese estado y ausente no filtra, como el {@code enabled} de los demas recursos.
 *
 * <p>La estacion y la funcion se alcanzan con join externo: la estacion es opcional, y con uno
 * interno un seccionador sin estacion desaparecia de la lista. Que la consulta de verdad lo devuelva,
 * tambien ordenada por la estacion, lo comprueba {@code DisconnectorLinkIT} contra PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisconnectorServiceTest {

    @Mock
    private DisconnectorRepository repository;
    @Mock
    private DisconnectorMapper mapper;
    @Mock
    private DisconnectorValidator validator;
    @Mock
    private DisconnectorBusiness business;
    @Mock
    private DisconnectorCriteriaSearchRepository criteriaSearchRepository;
    @Mock
    private MasterDataService masterDataService;

    @Mock
    private Root<Disconnector> root;
    @Mock
    private CriteriaQuery<Object> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Join<Disconnector, Station> station;
    @Mock
    private Join<Disconnector, DisconnectorFunction> function;
    @Mock
    private Path<Object> onLoad;
    @Captor
    private ArgumentCaptor<Specification<Disconnector>> specification;

    private DisconnectorService service;

    @BeforeEach
    void setUp() {
        service = new DisconnectorService(repository, mapper, validator, business, criteriaSearchRepository, masterDataService);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        doReturn(station).when(root).join("station", JoinType.LEFT);
        doReturn(function).when(root).join("disconnectorFunction", JoinType.LEFT);
        doReturn(onLoad).when(root).get("onLoad");
        when(cb.like(any(), anyString())).thenReturn(mock(Predicate.class));
    }

    /** Pide la lista con el filtro y aplica la {@link Specification} que llega al repositorio. */
    private void listWith(DisconnectorFilter filter) {
        service.getDisconnectors(PageRequest.of(0, 20), filter);
        verify(repository).findAll(specification.capture(), any(Pageable.class));
        specification.getValue().toPredicate(root, query, cb);
    }

    private static DisconnectorFilter onLoad(Boolean onLoad) {
        return new DisconnectorFilter(null, null, null, null, onLoad);
    }

    @Test
    @DisplayName("sin onLoad no filtra")
    void ausenteNoFiltra() {
        listWith(onLoad(null));

        verify(cb, never()).equal(any(Expression.class), any(Object.class));
    }

    @Test
    @DisplayName("false devuelve solo los que no estan en carga, ya no 'todo'")
    void falseFiltra() {
        listWith(onLoad(false));

        verify(cb).equal(onLoad, false);
    }

    @Test
    @DisplayName("true devuelve solo los que estan en carga")
    void trueFiltra() {
        listWith(onLoad(true));

        verify(cb).equal(onLoad, true);
    }

    @Test
    @DisplayName("la estacion y la funcion van con join externo, nunca interno")
    void joinsExternos() {
        listWith(new DisconnectorFilter("SEC", "HER", "Disc", "HER", null));

        verify(root).join("station", JoinType.LEFT);
        verify(root).join("disconnectorFunction", JoinType.LEFT);
        verify(root, never()).join(anyString());
        verify(root, never()).join(anyString(), eq(JoinType.INNER));
    }
}
