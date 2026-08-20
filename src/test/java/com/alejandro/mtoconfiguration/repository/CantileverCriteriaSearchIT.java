package com.alejandro.mtoconfiguration.repository;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.lov.CantileverType;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.CantileverCriteriaSearchRepository;
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
 * Lo propio de este repositorio son los rangos numéricos con sufijos Min/Max
 * (no From/To), que es lo que resuelven numberGe/numberLe con clave explícita.
 */
class CantileverCriteriaSearchIT extends AbstractCriteriaSearchIT {

    @Autowired
    private CantileverCriteriaSearchRepository repository;

    private Profile profile;

    @BeforeEach
    void seed() {
        ExecutionPackage paquete = executionPackage();
        Track via = track(paquete);
        profile = profile("P-001", via);

        CantileverType simple = cantileverType("SIM", "Simple");
        CantileverType doble = cantileverType("DOB", "Doble");

        em.persist(cantilever(new BigDecimal("1.100"), new BigDecimal("200"), simple));
        em.persist(cantilever(new BigDecimal("1.500"), new BigDecimal("250"), doble));
        em.persist(cantilever(new BigDecimal("1.900"), new BigDecimal("300"), simple));

        flushAndClear();
    }

    @Test
    void shouldFilterByClosedHeightRange() {
        assertThat(heights(searchCantilevers(Map.of(
                "cwHeightMin", new BigDecimal("1.200"),
                "cwHeightMax", new BigDecimal("1.600")))))
                .containsExactly(new BigDecimal("1.500"));
    }

    @Test
    void shouldFilterByOnlyTheMinimum() {
        assertThat(searchCantilevers(Map.of("cwHeightMin", new BigDecimal("1.500")))
                .getTotalElements()).isEqualTo(2);
    }

    @Test
    void shouldFilterByOnlyTheMaximum() {
        assertThat(searchCantilevers(Map.of("cwHeightMax", new BigDecimal("1.500")))
                .getTotalElements()).isEqualTo(2);
    }

    @Test
    void shouldIncludeTheBoundariesOfTheRange() {
        assertThat(searchCantilevers(Map.of(
                "cwHeightMin", new BigDecimal("1.500"),
                "cwHeightMax", new BigDecimal("1.500")))
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldFilterByStaggerRangeIndependentlyOfHeight() {
        assertThat(searchCantilevers(Map.of("staggerMin", new BigDecimal("250")))
                .getTotalElements()).isEqualTo(2);
    }

    @Test
    void shouldFilterByParentProfileId() {
        assertThat(searchCantilevers(Map.of("profileId", profile.getId()))
                .getTotalElements()).isEqualTo(3);
    }

    @Test
    void shouldFilterByCantileverTypeCode() {
        assertThat(searchCantilevers(Map.of("cantileverTypeCode", "SIM"))
                .getTotalElements()).isEqualTo(2);
    }

    private Page<Cantilever> searchCantilevers(Map<String, Object> filters) {
        return repository.criteriaSearchWithChildren(Cantilever.class, search(filters), em, Map.of());
    }

    private List<BigDecimal> heights(Page<Cantilever> page) {
        return page.getContent().stream().map(Cantilever::getCwHeight).toList();
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

    private Track track(ExecutionPackage executionPackage) {
        Track entity = new Track();
        entity.setName("VIA 1");
        entity.setEnabled(true);
        entity.setExecutionPackage(executionPackage);
        em.persist(entity);
        return entity;
    }

    private Profile profile(String profileId, Track track) {
        Profile entity = new Profile();
        entity.setProfileId(profileId);
        entity.setKp(new BigDecimal("10.000"));
        entity.setTrack(track);
        em.persist(entity);
        return entity;
    }

    private CantileverType cantileverType(String code, String description) {
        CantileverType entity = new CantileverType();
        entity.setCode(code);
        entity.setDescription(description);
        entity.setEnabled(true);
        em.persist(entity);
        return entity;
    }

    private Cantilever cantilever(BigDecimal cwHeight, BigDecimal stagger, CantileverType type) {
        Cantilever entity = new Cantilever();
        entity.setCwHeight(cwHeight);
        entity.setStagger(stagger);
        entity.setCantileverType(type);
        entity.setProfile(profile);
        return entity;
    }
}
