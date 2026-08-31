package com.alejandro.mtoconfiguration.repository;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorCriteriaSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo propio de este repositorio es el filtro booleano con {@code eq}, que si distingue los dos
 * valores: a diferencia del {@code isTrue} de Disconnector, aqui {@code enabled=false} devuelve
 * los deshabilitados en vez de no filtrar. Las dos formas conviven en el proyecto y esta es la
 * pareja de tests que documenta la diferencia.
 */
class SectionInsulatorCriteriaSearchIT extends AbstractCriteriaSearchIT {

    @Autowired
    private SectionInsulatorCriteriaSearchRepository repository;

    @BeforeEach
    void seed() {
        ExecutionPackage paquete = executionPackage();

        Station atocha = station("ATOCHA", paquete);
        Station chamartin = station("CHAMARTIN", paquete);

        em.persist(sectionInsulator("AISL-1", true, atocha));
        em.persist(sectionInsulator("AISL-2", false, atocha));
        em.persist(sectionInsulator("SECC-1", true, chamartin));
        em.persist(sectionInsulator("HUERFANO", true, null));

        flushAndClear();
    }

    @Test
    void shouldReturnEverythingWhenThereAreNoFilters() {
        assertThat(searchInsulators(Map.of()).getTotalElements()).isEqualTo(4);
    }

    @Test
    void shouldFilterByNameCaseInsensitivelyAndPartially() {
        assertThat(names(searchInsulators(Map.of("name", "aisl"))))
                .containsExactlyInAnyOrder("AISL-1", "AISL-2");
    }

    @Test
    void shouldFilterByEnabledTrue() {
        assertThat(names(searchInsulators(Map.of("enabled", true))))
                .containsExactlyInAnyOrder("AISL-1", "SECC-1", "HUERFANO");
    }

    @Test
    void shouldFilterByEnabledFalse() {
        // A diferencia del isTrue de Disconnector, eq si distingue: false devuelve los
        // deshabilitados, no la tabla entera.
        assertThat(names(searchInsulators(Map.of("enabled", false))))
                .containsExactly("AISL-2");
    }

    @Test
    void shouldFilterByAssociatedStationName() {
        assertThat(names(searchInsulators(Map.of("stationName", "atocha"))))
                .containsExactlyInAnyOrder("AISL-1", "AISL-2");
    }

    @Test
    void shouldNotReturnRowsWithoutAssociationWhenFilteringByIt() {
        assertThat(names(searchInsulators(Map.of("stationName", "a"))))
                .doesNotContain("HUERFANO");
    }

    @Test
    void shouldCombineFiltersWithAnd() {
        assertThat(names(searchInsulators(Map.of("name", "aisl", "enabled", true))))
                .containsExactly("AISL-1");
    }

    @Test
    void shouldMatchSearchTextAcrossOwnAndAssociatedColumns() {
        // por columna propia
        assertThat(names(searchInsulators(Map.of("searchText", "huerfano"))))
                .containsExactly("HUERFANO");

        // por la estacion, que no es columna de SectionInsulator
        assertThat(names(searchInsulators(Map.of("searchText", "chamartin"))))
                .containsExactly("SECC-1");
    }

    @Test
    void shouldReturnEmptyPageWhenNothingMatches() {
        Page<SectionInsulator> result = searchInsulators(Map.of("name", "NO-EXISTE"));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void shouldSortByWhitelistedColumn() {
        assertThat(names(repository.criteriaSearchWithChildren(
                SectionInsulator.class, search(Map.of(), "name", "asc"), em, Map.of())))
                .containsExactly("AISL-1", "AISL-2", "HUERFANO", "SECC-1");
    }

    @Test
    void shouldNotFailWhenSortByIsNotWhitelisted() {
        Page<SectionInsulator> result = repository.criteriaSearchWithChildren(
                SectionInsulator.class, search(Map.of(), "createUser", "asc"), em, Map.of());

        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    private Page<SectionInsulator> searchInsulators(Map<String, Object> filters) {
        return repository.criteriaSearchWithChildren(SectionInsulator.class, search(filters), em, Map.of());
    }

    private List<String> names(Page<SectionInsulator> page) {
        return page.getContent().stream().map(SectionInsulator::getName).toList();
    }

    private ExecutionPackage executionPackage() {
        ExecutionPackage entity = new ExecutionPackage();
        entity.setName("PAQUETE 1");
        entity.setInitialPackage(false);
        entity.setLength(1000L);
        entity.setStartDate(LocalDate.of(2026, 1, 1));
        entity.setEndDate(LocalDate.of(2026, 12, 31));
        entity.setEnabled(true);
        em.persist(entity);
        return entity;
    }

    private Station station(String name, ExecutionPackage executionPackage) {
        Station entity = new Station();
        entity.setName(name);
        entity.setExecutionPackage(executionPackage);
        em.persist(entity);
        return entity;
    }

    private SectionInsulator sectionInsulator(String name, boolean enabled, Station station) {
        SectionInsulator entity = new SectionInsulator();
        entity.setName(name);
        entity.setEnabled(enabled);
        entity.setStation(station);
        return entity;
    }
}
