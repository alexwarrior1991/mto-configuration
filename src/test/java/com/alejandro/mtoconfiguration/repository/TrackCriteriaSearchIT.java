package com.alejandro.mtoconfiguration.repository;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackCriteriaSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre los cuatro mecanismos del predicado: LIKE sobre columna propia, igualdad
 * booleana, LIKE sobre asociación, y searchText cruzando varias tablas. Además la
 * lista blanca de ordenación.
 */
class TrackCriteriaSearchIT extends AbstractCriteriaSearchIT {

    @Autowired
    private TrackCriteriaSearchRepository repository;

    @BeforeEach
    void seed() {
        ExecutionPackage paqueteNorte = executionPackage("PAQUETE NORTE");
        ExecutionPackage paqueteSur = executionPackage("PAQUETE SUR");

        Station atocha = station("ATOCHA", paqueteNorte);
        Station chamartin = station("CHAMARTIN", paqueteSur);

        em.persist(track("VIA 1", true, paqueteNorte, atocha));
        em.persist(track("VIA 2", true, paqueteNorte, chamartin));
        em.persist(track("VIA 3", false, paqueteSur, atocha));
        em.persist(track("DESVIO", true, paqueteSur, chamartin));

        flushAndClear();
    }

    @Test
    void shouldReturnEverythingWhenThereAreNoFilters() {
        Page<Track> result = searchTracks(Map.of());

        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    @Test
    void shouldFilterByNameCaseInsensitivelyAndPartially() {
        assertThat(names(searchTracks(Map.of("name", "via"))))
                .containsExactlyInAnyOrder("VIA 1", "VIA 2", "VIA 3");
    }

    @Test
    void shouldFilterByEnabledFlag() {
        assertThat(names(searchTracks(Map.of("enabled", false))))
                .containsExactly("VIA 3");
    }

    @Test
    void shouldFilterByAssociatedExecutionPackageName() {
        assertThat(names(searchTracks(Map.of("executionPackageName", "norte"))))
                .containsExactlyInAnyOrder("VIA 1", "VIA 2");
    }

    @Test
    void shouldFilterByAssociatedStationName() {
        assertThat(names(searchTracks(Map.of("stationName", "atocha"))))
                .containsExactlyInAnyOrder("VIA 1", "VIA 3");
    }

    @Test
    void shouldCombineFiltersWithAnd() {
        assertThat(names(searchTracks(Map.of("name", "via", "stationName", "atocha"))))
                .containsExactlyInAnyOrder("VIA 1", "VIA 3");
    }

    @Test
    void shouldMatchSearchTextAcrossOwnAndAssociatedColumns() {
        // "CHAMARTIN" no está en ninguna columna de Track: sólo puede venir del join
        assertThat(names(searchTracks(Map.of("searchText", "chamartin"))))
                .containsExactlyInAnyOrder("VIA 2", "DESVIO");

        // y sigue encontrando por la columna propia
        assertThat(names(searchTracks(Map.of("searchText", "desvio"))))
                .containsExactly("DESVIO");
    }

    @Test
    void shouldReturnEmptyPageWhenNothingMatches() {
        Page<Track> result = searchTracks(Map.of("name", "NO-EXISTE"));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void shouldSortByWhitelistedColumn() {
        assertThat(names(repository.criteriaSearchWithChildren(
                Track.class, search(Map.of(), "name", "asc"), em, Map.of())))
                .containsExactly("DESVIO", "VIA 1", "VIA 2", "VIA 3");
    }

    @Test
    void shouldNotFailWhenSortByIsNotWhitelisted() {
        // sortBy llega del cliente: una ruta arbitraria no debe tumbar la consulta.
        // SortPaths devuelve null y criteriaSearchWithChildren omite el ORDER BY.
        Page<Track> result = repository.criteriaSearchWithChildren(
                Track.class, search(Map.of(), "createUser", "asc"), em, Map.of());

        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    private Page<Track> searchTracks(Map<String, Object> filters) {
        return repository.criteriaSearchWithChildren(Track.class, search(filters), em, Map.of());
    }

    private List<String> names(Page<Track> page) {
        return page.getContent().stream().map(Track::getName).toList();
    }

    private ExecutionPackage executionPackage(String name) {
        ExecutionPackage entity = new ExecutionPackage();
        entity.setName(name);
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

    private Track track(String name, boolean enabled, ExecutionPackage executionPackage, Station station) {
        Track entity = new Track();
        entity.setName(name);
        entity.setEnabled(enabled);
        entity.setExecutionPackage(executionPackage);
        entity.setStation(station);
        return entity;
    }
}
