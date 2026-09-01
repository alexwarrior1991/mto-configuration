package com.alejandro.mtoconfiguration.repository;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.lov.CantileverType;
import com.alejandro.mtoconfiguration.entity.lov.SteadyArmType;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SteadyArmCriteriaSearchRepository;
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
 * Lo propio de este repositorio es la busqueda libre sobre una columna <b>numerica</b>
 * ({@code searchNumeric}), que solo se aplica si el texto buscado es todo digitos y no empieza
 * por cero. Esas dos condiciones no se ven en el predicado y cambian el resultado por completo:
 * con un texto no numerico la parte numerica del OR desaparece y solo queda la del tipo.
 *
 * <p>Cubre tambien los rangos con sufijos Min/Max y la igualdad por id de la asociacion, que es
 * el unico filtro de este tipo en todo el proyecto.</p>
 */
class SteadyArmCriteriaSearchIT extends AbstractCriteriaSearchIT {

    @Autowired
    private SteadyArmCriteriaSearchRepository repository;

    private Cantilever primeraMensula;
    private Cantilever segundaMensula;

    @BeforeEach
    void seed() {
        ExecutionPackage paquete = executionPackage();
        Track via = track(paquete);
        Profile perfil = profile(via);
        CantileverType tipoMensula = cantileverType();

        primeraMensula = cantilever(perfil, tipoMensula);
        segundaMensula = cantilever(perfil, tipoMensula);

        SteadyArmType corto = steadyArmType("CRT", "Corto");
        SteadyArmType largo = steadyArmType("LRG", "Largo");

        em.persist(steadyArm(200L, corto, primeraMensula));
        em.persist(steadyArm(250L, largo, segundaMensula));
        em.persist(steadyArm(300L, corto, null));

        flushAndClear();
    }

    @Test
    void shouldReturnEverythingWhenThereAreNoFilters() {
        assertThat(searchSteadyArms(Map.of()).getTotalElements()).isEqualTo(3);
    }

    @Test
    void shouldFilterByClosedLengthRange() {
        assertThat(lengths(searchSteadyArms(Map.of("lengthMin", 220L, "lengthMax", 280L))))
                .containsExactly(250L);
    }

    @Test
    void shouldFilterByOnlyTheMinimum() {
        assertThat(lengths(searchSteadyArms(Map.of("lengthMin", 250L))))
                .containsExactlyInAnyOrder(250L, 300L);
    }

    @Test
    void shouldFilterByOnlyTheMaximum() {
        assertThat(lengths(searchSteadyArms(Map.of("lengthMax", 250L))))
                .containsExactlyInAnyOrder(200L, 250L);
    }

    @Test
    void shouldIncludeTheBoundsOfTheRange() {
        // numberGe/numberLe, no gt/lt: los extremos entran.
        assertThat(lengths(searchSteadyArms(Map.of("lengthMin", 200L, "lengthMax", 200L))))
                .containsExactly(200L);
    }

    @Test
    void shouldFilterByAssociatedCantileverId() {
        assertThat(lengths(searchSteadyArms(Map.of("cantileverId", primeraMensula.getId()))))
                .containsExactly(200L);
    }

    @Test
    void shouldFilterByAssociatedTypeCode() {
        assertThat(lengths(searchSteadyArms(Map.of("steadyArmTypeCode", "crt"))))
                .containsExactlyInAnyOrder(200L, 300L);
    }

    @Test
    void shouldMatchNumericSearchAgainstTheLengthColumn() {
        // searchNumeric compara la columna convertida a texto: "25" casa con 250.
        assertThat(lengths(searchSteadyArms(Map.of("searchText", "25"))))
                .containsExactly(250L);
    }

    @Test
    void shouldNotApplyNumericSearchWhenTheTextStartsWithZero() {
        // isValidNumericSearch descarta los valores que empiezan por cero, asi que la rama
        // numerica del OR desaparece y solo queda la del codigo de tipo, que tampoco casa.
        assertThat(searchSteadyArms(Map.of("searchText", "0250")).getContent()).isEmpty();
    }

    @Test
    void shouldFallBackToTheTypeCodeWhenTheSearchIsNotNumeric() {
        assertThat(lengths(searchSteadyArms(Map.of("searchText", "lrg"))))
                .containsExactly(250L);
    }

    @Test
    void shouldReadTheSearchKeyBeforeSearchText() {
        // extractSearchValue mira primero "search" y solo si viene en blanco cae a "searchText".
        assertThat(lengths(searchSteadyArms(Map.of("search", "lrg", "searchText", "crt"))))
                .containsExactly(250L);
    }

    @Test
    void shouldCombineRangeAndAssociationFilters() {
        assertThat(lengths(searchSteadyArms(Map.of("lengthMin", 200L, "steadyArmTypeCode", "crt"))))
                .containsExactlyInAnyOrder(200L, 300L);
    }

    @Test
    void shouldReturnEmptyPageWhenNothingMatches() {
        Page<SteadyArm> result = searchSteadyArms(Map.of("lengthMin", 9999L));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void shouldSortByWhitelistedColumn() {
        assertThat(lengths(repository.criteriaSearchWithChildren(
                SteadyArm.class, search(Map.of(), "length", "desc"), em, Map.of())))
                .containsExactly(300L, 250L, 200L);
    }

    @Test
    void shouldNotFailWhenSortByIsNotWhitelisted() {
        Page<SteadyArm> result = repository.criteriaSearchWithChildren(
                SteadyArm.class, search(Map.of(), "createUser", "asc"), em, Map.of());

        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    private Page<SteadyArm> searchSteadyArms(Map<String, Object> filters) {
        return repository.criteriaSearchWithChildren(SteadyArm.class, search(filters), em, Map.of());
    }

    private List<Long> lengths(Page<SteadyArm> page) {
        return page.getContent().stream().map(SteadyArm::getLength).toList();
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

    private Track track(ExecutionPackage executionPackage) {
        Track entity = new Track();
        entity.setName("VIA 1");
        entity.setEnabled(true);
        entity.setExecutionPackage(executionPackage);
        em.persist(entity);
        return entity;
    }

    private Profile profile(Track track) {
        Profile entity = new Profile();
        entity.setProfileId("P-001");
        entity.setKp(new BigDecimal("10.000"));
        entity.setTrack(track);
        em.persist(entity);
        return entity;
    }

    private CantileverType cantileverType() {
        CantileverType entity = new CantileverType();
        entity.setCode("SIM");
        entity.setDescription("Simple");
        entity.setEnabled(true);
        em.persist(entity);
        return entity;
    }

    private Cantilever cantilever(Profile profile, CantileverType type) {
        Cantilever entity = new Cantilever();
        entity.setCwHeight(new BigDecimal("1.100"));
        entity.setStagger(new BigDecimal("200"));
        entity.setCantileverType(type);
        entity.setProfile(profile);
        em.persist(entity);
        return entity;
    }

    private SteadyArmType steadyArmType(String code, String description) {
        SteadyArmType entity = new SteadyArmType();
        entity.setCode(code);
        entity.setDescription(description);
        entity.setEnabled(true);
        em.persist(entity);
        return entity;
    }

    private SteadyArm steadyArm(Long length, SteadyArmType type, Cantilever cantilever) {
        SteadyArm entity = new SteadyArm();
        entity.setLength(length);
        entity.setSteadyArmType(type);
        entity.setCantilever(cantilever);
        return entity;
    }
}
