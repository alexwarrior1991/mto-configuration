package com.alejandro.mtoconfiguration.repository;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.lov.PoleType;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileCriteriaSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo propio de este repositorio son dos cosas: el filtro por código de LOV
 * asociado, y stationName, que cuelga de track.station y por tanto necesita DOS
 * saltos de join en lugar de uno.
 */
class ProfileCriteriaSearchIT extends AbstractCriteriaSearchIT {

    @Autowired
    private ProfileCriteriaSearchRepository repository;

    @BeforeEach
    void seed() {
        ExecutionPackage paquete = executionPackage();

        Station atocha = station("ATOCHA", paquete);
        Station chamartin = station("CHAMARTIN", paquete);

        Track via1 = track("VIA 1", paquete, atocha);
        Track via2 = track("VIA 2", paquete, chamartin);

        PoleType hea = poleType("HEA", "Poste HEA");
        PoleType ipn = poleType("IPN", "Poste IPN");

        em.persist(profile("P-001", new BigDecimal("10.500"), via1, hea));
        em.persist(profile("P-002", new BigDecimal("20.500"), via1, ipn));
        em.persist(profile("P-003", new BigDecimal("30.500"), via2, hea));

        flushAndClear();
    }

    @Test
    void shouldFilterByProfileBusinessCode() {
        assertThat(codes(searchProfiles(Map.of("profileId", "P-00"))))
                .containsExactlyInAnyOrder("P-001", "P-002", "P-003");

        assertThat(codes(searchProfiles(Map.of("profileId", "P-002"))))
                .containsExactly("P-002");
    }

    @Test
    void shouldFilterByAssociatedTrackName() {
        assertThat(codes(searchProfiles(Map.of("trackName", "via 1"))))
                .containsExactlyInAnyOrder("P-001", "P-002");
    }

    @Test
    void shouldFilterByStationNameThroughTwoJoinHops() {
        // stationName no cuelga de Profile: hay que atravesar track -> station
        assertThat(codes(searchProfiles(Map.of("stationName", "chamartin"))))
                .containsExactly("P-003");
    }

    @Test
    void shouldFilterByLovCode() {
        assertThat(codes(searchProfiles(Map.of("poleTypeCode", "HEA"))))
                .containsExactlyInAnyOrder("P-001", "P-003");
    }

    @Test
    void shouldCombineLovCodeAndTrackFilters() {
        assertThat(codes(searchProfiles(Map.of("poleTypeCode", "HEA", "trackName", "via 1"))))
                .containsExactly("P-001");
    }

    @Test
    void shouldReturnEverythingWhenLovFilterIsBlank() {
        // Un filtro en blanco NO debe crear el join ni acotar el resultado.
        assertThat(searchProfiles(Map.of("poleTypeCode", "")).getTotalElements()).isEqualTo(3);
    }

    @Test
    void shouldMatchSearchTextAgainstTrackName() {
        assertThat(codes(searchProfiles(Map.of("searchText", "via 2"))))
                .containsExactly("P-003");
    }

    private Page<Profile> searchProfiles(Map<String, Object> filters) {
        return repository.criteriaSearchWithChildren(Profile.class, search(filters), em, Map.of());
    }

    private List<String> codes(Page<Profile> page) {
        return page.getContent().stream().map(Profile::getProfileId).toList();
    }

    private ExecutionPackage executionPackage() {
        ExecutionPackage entity = new ExecutionPackage();
        entity.setName("PAQUETE");
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

    private Track track(String name, ExecutionPackage executionPackage, Station station) {
        Track entity = new Track();
        entity.setName(name);
        entity.setEnabled(true);
        entity.setExecutionPackage(executionPackage);
        entity.setStation(station);
        em.persist(entity);
        return entity;
    }

    private PoleType poleType(String code, String description) {
        PoleType entity = new PoleType();
        entity.setCode(code);
        entity.setDescription(description);
        entity.setEnabled(true);
        em.persist(entity);
        return entity;
    }

    private Profile profile(String profileId, BigDecimal kp, Track track, PoleType poleType) {
        Profile entity = new Profile();
        entity.setProfileId(profileId);
        entity.setKp(kp);
        entity.setTrack(track);
        entity.setPoleType(poleType);
        return entity;
    }
}
