package com.alejandro.mtoconfiguration.repository;

import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.lov.DisconnectorFunction;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorCriteriaSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo propio de este repositorio es el filtro booleano con {@code isTrue(columna, filtro)}, que
 * solo añade condicion cuando el valor recibido es {@code TRUE}. Mandar {@code false} no filtra
 * por "los que no estan en carga": no filtra nada. Es una asimetria que no se ve leyendo el
 * predicado y que conviene tener fijada.
 *
 * <p>Ademas, la busqueda libre cruza tres tablas (propia, estacion y funcion), asi que sirve para
 * comprobar que los joins se reutilizan en lugar de multiplicarse.</p>
 */
class DisconnectorCriteriaSearchIT extends AbstractCriteriaSearchIT {

    @Autowired
    private DisconnectorCriteriaSearchRepository repository;

    @BeforeEach
    void seed() {
        ExecutionPackage paquete = executionPackage();

        Station atocha = station("ATOCHA", paquete);
        Station chamartin = station("CHAMARTIN", paquete);

        DisconnectorFunction seccionamiento = disconnectorFunction("SEC", "Seccionamiento de via");
        DisconnectorFunction puesta = disconnectorFunction("PAT", "Puesta a tierra");

        em.persist(disconnector("SECC-1", true, atocha, seccionamiento));
        em.persist(disconnector("SECC-2", false, atocha, puesta));
        em.persist(disconnector("PAT-1", true, chamartin, puesta));
        em.persist(disconnector("AISLADO", false, null, null));

        flushAndClear();
    }

    @Test
    void shouldReturnEverythingWhenThereAreNoFilters() {
        assertThat(searchDisconnectors(Map.of()).getTotalElements()).isEqualTo(4);
    }

    @Test
    void shouldFilterByNameCaseInsensitivelyAndPartially() {
        assertThat(names(searchDisconnectors(Map.of("name", "secc"))))
                .containsExactlyInAnyOrder("SECC-1", "SECC-2");
    }

    @Test
    void shouldFilterByOnLoadWhenTrue() {
        assertThat(names(searchDisconnectors(Map.of("onLoad", true))))
                .containsExactlyInAnyOrder("SECC-1", "PAT-1");
    }

    @Test
    void shouldIgnoreOnLoadFilterWhenFalse() {
        // isTrue(columna, filtro) devuelve null salvo que el valor sea TRUE, de modo que
        // onLoad=false NO significa "los que no estan en carga": significa "sin filtro".
        assertThat(searchDisconnectors(Map.of("onLoad", false)).getTotalElements()).isEqualTo(4);
    }

    @Test
    void shouldFilterByAssociatedStationName() {
        assertThat(names(searchDisconnectors(Map.of("stationName", "atocha"))))
                .containsExactlyInAnyOrder("SECC-1", "SECC-2");
    }

    @Test
    void shouldFilterByAssociatedFunctionDescription() {
        assertThat(names(searchDisconnectors(Map.of("functionName", "tierra"))))
                .containsExactlyInAnyOrder("SECC-2", "PAT-1");
    }

    @Test
    void shouldNotReturnRowsWithoutAssociationWhenFilteringByIt() {
        // AISLADO no tiene estacion: el LEFT JOIN lo trae con null y el LIKE lo descarta.
        assertThat(names(searchDisconnectors(Map.of("stationName", "a"))))
                .doesNotContain("AISLADO");
    }

    @Test
    void shouldCombineFiltersWithAnd() {
        assertThat(names(searchDisconnectors(Map.of("name", "secc", "stationName", "atocha"))))
                .containsExactlyInAnyOrder("SECC-1", "SECC-2");

        assertThat(searchDisconnectors(Map.of("name", "secc", "stationName", "chamartin")).getContent())
                .isEmpty();
    }

    @Test
    void shouldMatchSearchTextAcrossOwnAndBothAssociatedColumns() {
        // por columna propia
        assertThat(names(searchDisconnectors(Map.of("searchText", "aislado"))))
                .containsExactly("AISLADO");

        // por la estacion, que no es columna de Disconnector
        assertThat(names(searchDisconnectors(Map.of("searchText", "chamartin"))))
                .containsExactly("PAT-1");

        // por la descripcion de la funcion
        assertThat(names(searchDisconnectors(Map.of("searchText", "seccionamiento"))))
                .containsExactly("SECC-1");
    }

    @Test
    void shouldReuseJoinWhenFilterAndSearchTextHitTheSameAssociation() {
        // stationName y searchText navegan ambos a "station": si cada uno creara su propio join,
        // el producto cartesiano duplicaria las filas.
        Page<Disconnector> result = searchDisconnectors(Map.of(
                "stationName", "atocha", "searchText", "atocha"));

        assertThat(names(result)).containsExactlyInAnyOrder("SECC-1", "SECC-2");
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyPageWhenNothingMatches() {
        Page<Disconnector> result = searchDisconnectors(Map.of("name", "NO-EXISTE"));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void shouldSortByWhitelistedColumn() {
        assertThat(names(repository.criteriaSearchWithChildren(
                Disconnector.class, search(Map.of(), "name", "asc"), em, Map.of())))
                .containsExactly("AISLADO", "PAT-1", "SECC-1", "SECC-2");
    }

    @Test
    void shouldNotFailWhenSortByIsNotWhitelisted() {
        Page<Disconnector> result = repository.criteriaSearchWithChildren(
                Disconnector.class, search(Map.of(), "createUser", "asc"), em, Map.of());

        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    private Page<Disconnector> searchDisconnectors(Map<String, Object> filters) {
        return repository.criteriaSearchWithChildren(Disconnector.class, search(filters), em, Map.of());
    }

    private List<String> names(Page<Disconnector> page) {
        return page.getContent().stream().map(Disconnector::getName).toList();
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

    private DisconnectorFunction disconnectorFunction(String code, String description) {
        DisconnectorFunction entity = new DisconnectorFunction();
        entity.setCode(code);
        entity.setDescription(description);
        entity.setEnabled(true);
        em.persist(entity);
        return entity;
    }

    private Disconnector disconnector(String name, boolean onLoad, Station station, DisconnectorFunction function) {
        Disconnector entity = new Disconnector();
        entity.setName(name);
        entity.setOnLoad(onLoad);
        entity.setStation(station);
        entity.setDisconnectorFunction(function);
        return entity;
    }
}
