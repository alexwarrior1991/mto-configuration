package com.alejandro.mtoconfiguration.repository;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.infrastructure.TrackAccessPoint;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointKind;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointSide;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackAccessPointCriteriaSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La búsqueda por criterios de los puntos de acceso a la vía (V28): sus dos enumerados se comparan
 * como tales, con la lectura tolerante del aislador, y sus dos booleanos con {@code eq}, que distingue
 * {@code false} de «sin filtro». La estación es opcional: filtrar por ella deja fuera al que no la
 * tiene, y la búsqueda por texto lo encuentra por otra columna.
 */
class TrackAccessPointCriteriaSearchIT extends AbstractCriteriaSearchIT {

    @Autowired
    private TrackAccessPointCriteriaSearchRepository repository;

    private Track via1;

    @BeforeEach
    void seed() {
        ExecutionPackage paquete = executionPackage();

        Station atocha = station("ATOCHA", paquete);

        via1 = track("VIA 1", paquete);
        Track via2 = track("VIA 2", paquete);

        em.persist(accessPoint("ACC-1", via1, atocha, TrackAccessPointKind.PREPARED_ACCESS,
                TrackAccessPointSide.LEFT, true, true));
        em.persist(accessPoint("PN-2", via1, null, TrackAccessPointKind.LEVEL_CROSSING,
                TrackAccessPointSide.BOTH, true, false));
        em.persist(accessPoint("COCHERA", via2, null, TrackAccessPointKind.DEPOT,
                TrackAccessPointSide.RIGHT, false, true));

        flushAndClear();
    }

    @Test
    void shouldReturnEverythingWhenThereAreNoFilters() {
        assertThat(searchAccessPoints(Map.of()).getTotalElements()).isEqualTo(3);
    }

    @Test
    void shouldFilterByKindAndSideAsEnums() {
        assertThat(names(searchAccessPoints(Map.of("kind", "LEVEL_CROSSING")))).containsExactly("PN-2");
        assertThat(names(searchAccessPoints(Map.of("side", "right")))).containsExactly("COCHERA");
    }

    @Test
    void shouldNotReturnAnythingForAKindThatDoesNotExist() {
        assertThat(searchAccessPoints(Map.of("kind", "HELIPUERTO")).getTotalElements()).isZero();
    }

    @Test
    void shouldFilterByBooleansIncludingFalse() {
        assertThat(names(searchAccessPoints(Map.of("roadRailAccess", false)))).containsExactly("COCHERA");
        assertThat(names(searchAccessPoints(Map.of("enabled", false)))).containsExactly("PN-2");
    }

    @Test
    void shouldFilterByTrackIdAndTrackName() {
        assertThat(names(searchAccessPoints(Map.of("trackId", via1.getId()))))
                .containsExactlyInAnyOrder("ACC-1", "PN-2");
        assertThat(names(searchAccessPoints(Map.of("trackName", "via 2")))).containsExactly("COCHERA");
    }

    @Test
    void shouldNotReturnRowsWithoutStationWhenFilteringByIt() {
        assertThat(names(searchAccessPoints(Map.of("stationName", "atocha")))).containsExactly("ACC-1");
    }

    @Test
    void shouldMatchSearchTextAcrossOwnAndAssociatedColumns() {
        assertThat(names(searchAccessPoints(Map.of("searchText", "pn")))).containsExactly("PN-2");
        // por la via, tambien el que no tiene estacion
        assertThat(names(searchAccessPoints(Map.of("searchText", "via 1"))))
                .containsExactlyInAnyOrder("ACC-1", "PN-2");
    }

    @Test
    void shouldSortByWhitelistedColumn() {
        assertThat(names(repository.criteriaSearchWithChildren(
                TrackAccessPoint.class, search(Map.of(), "name", "asc"), em, Map.of())))
                .containsExactly("ACC-1", "COCHERA", "PN-2");
    }

    @Test
    void shouldNotFailWhenSortByIsNotWhitelisted() {
        Page<TrackAccessPoint> result = repository.criteriaSearchWithChildren(
                TrackAccessPoint.class, search(Map.of(), "createUser", "asc"), em, Map.of());

        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    private Page<TrackAccessPoint> searchAccessPoints(Map<String, Object> filters) {
        return repository.criteriaSearchWithChildren(TrackAccessPoint.class, search(filters), em, Map.of());
    }

    private List<String> names(Page<TrackAccessPoint> page) {
        return page.getContent().stream().map(TrackAccessPoint::getName).toList();
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

    private Track track(String name, ExecutionPackage executionPackage) {
        Track entity = new Track();
        entity.setName(name);
        entity.setEnabled(true);
        entity.setExecutionPackage(executionPackage);
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

    private static TrackAccessPoint accessPoint(String name, Track track, Station station, TrackAccessPointKind kind,
                                                TrackAccessPointSide side, boolean roadRailAccess, boolean enabled) {
        TrackAccessPoint entity = new TrackAccessPoint();
        entity.setName(name);
        entity.setTrack(track);
        entity.setStation(station);
        entity.setKind(kind);
        entity.setSide(side);
        entity.setRoadRailAccess(roadRailAccess);
        entity.setEnabled(enabled);
        return entity;
    }
}
