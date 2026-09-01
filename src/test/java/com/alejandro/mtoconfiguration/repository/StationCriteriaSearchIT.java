package com.alejandro.mtoconfiguration.repository;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.StationCriteriaSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo propio de este repositorio es que uno de sus filtros ({@code trackName}) navega a una
 * coleccion, no a una asociacion simple. Un LEFT JOIN contra una coleccion multiplica la fila
 * padre por cada hijo que casa, asi que una estacion con dos vias coincidentes aparecería dos
 * veces si la deduplicacion de {@code criteriaSearchWithChildren} no funcionase. Ese es el caso
 * que ningun otro IT de busqueda cubre.
 */
class StationCriteriaSearchIT extends AbstractCriteriaSearchIT {

    @Autowired
    private StationCriteriaSearchRepository repository;

    @BeforeEach
    void seed() {
        ExecutionPackage paqueteNorte = executionPackage("PAQUETE NORTE");
        ExecutionPackage paqueteSur = executionPackage("PAQUETE SUR");

        Station atocha = station("ATOCHA", paqueteNorte);
        Station chamartin = station("CHAMARTIN", paqueteSur);
        Station recoletos = station("RECOLETOS", paqueteNorte);

        // ATOCHA tiene DOS vias que casan con "via": si la consulta no deduplica, sale repetida.
        track("VIA 1", atocha, paqueteNorte);
        track("VIA 2", atocha, paqueteNorte);
        track("ANDEN 1", chamartin, paqueteSur);

        flushAndClear();
    }

    @Test
    void shouldReturnEverythingWhenThereAreNoFilters() {
        assertThat(searchStations(Map.of()).getTotalElements()).isEqualTo(3);
    }

    @Test
    void shouldFilterByNameCaseInsensitivelyAndPartially() {
        assertThat(names(searchStations(Map.of("name", "ato"))))
                .containsExactly("ATOCHA");
    }

    @Test
    void shouldFilterByAssociatedExecutionPackageName() {
        assertThat(names(searchStations(Map.of("executionPackageName", "norte"))))
                .containsExactlyInAnyOrder("ATOCHA", "RECOLETOS");
    }

    @Test
    void shouldFilterByTrackNameNavigatingTheCollection() {
        assertThat(names(searchStations(Map.of("trackName", "anden"))))
                .containsExactly("CHAMARTIN");
    }

    @Test
    void shouldNotDuplicateParentWhenSeveralChildrenMatch() {
        // ATOCHA tiene VIA 1 y VIA 2: el join contra la coleccion devuelve dos filas para la
        // misma estacion y la consulta debe entregar una sola, tambien en el total.
        Page<Station> result = searchStations(Map.of("trackName", "via"));

        assertThat(names(result)).containsExactly("ATOCHA");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldCombineFiltersWithAnd() {
        assertThat(names(searchStations(Map.of("name", "ato", "executionPackageName", "norte"))))
                .containsExactly("ATOCHA");

        assertThat(searchStations(Map.of("name", "ato", "executionPackageName", "sur")).getContent())
                .isEmpty();
    }

    @Test
    void shouldMatchSearchTextAcrossOwnAndAssociatedColumns() {
        // "SUR" no esta en ninguna columna de Station: solo puede venir del join
        assertThat(names(searchStations(Map.of("searchText", "sur"))))
                .containsExactly("CHAMARTIN");

        // y sigue encontrando por la columna propia
        assertThat(names(searchStations(Map.of("searchText", "recoletos"))))
                .containsExactly("RECOLETOS");
    }

    @Test
    void shouldNotIncludeTrackNameInFreeTextSearch() {
        // searchText solo mira nombre propio y paquete: la via no entra. Queda escrito porque es
        // una asimetria respecto al filtro trackName, que si navega a la coleccion.
        assertThat(searchStations(Map.of("searchText", "anden")).getContent()).isEmpty();
    }

    @Test
    void shouldReturnEmptyPageWhenNothingMatches() {
        Page<Station> result = searchStations(Map.of("name", "NO-EXISTE"));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void shouldSortByWhitelistedColumn() {
        assertThat(names(repository.criteriaSearchWithChildren(
                Station.class, search(Map.of(), "name", "asc"), em, Map.of())))
                .containsExactly("ATOCHA", "CHAMARTIN", "RECOLETOS");
    }

    @Test
    void shouldSortByWhitelistedAssociatedColumn() {
        assertThat(names(repository.criteriaSearchWithChildren(
                Station.class, search(Map.of(), "executionPackage.name", "desc"), em, Map.of())))
                .startsWith("CHAMARTIN");
    }

    @Test
    void shouldNotFailWhenSortByIsNotWhitelisted() {
        // sortBy llega del cliente: una ruta arbitraria no debe tumbar la consulta.
        Page<Station> result = repository.criteriaSearchWithChildren(
                Station.class, search(Map.of(), "createUser", "asc"), em, Map.of());

        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    private Page<Station> searchStations(Map<String, Object> filters) {
        return repository.criteriaSearchWithChildren(Station.class, search(filters), em, Map.of());
    }

    private List<String> names(Page<Station> page) {
        return page.getContent().stream().map(Station::getName).toList();
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

    private Track track(String name, Station station, ExecutionPackage executionPackage) {
        Track entity = new Track();
        entity.setName(name);
        entity.setEnabled(true);
        entity.setStation(station);
        entity.setExecutionPackage(executionPackage);
        em.persist(entity);
        return entity;
    }
}
