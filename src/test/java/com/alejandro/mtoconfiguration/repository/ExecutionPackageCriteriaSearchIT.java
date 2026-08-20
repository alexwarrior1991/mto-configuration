package com.alejandro.mtoconfiguration.repository;

import com.alejandro.mtoconfiguration.entity.configuration.BusinessEntity;
import com.alejandro.mtoconfiguration.entity.lov.ComercialEntityType;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ExecutionPackageCriteriaSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El caso propio de este repositorio es el rango de fechas: startDate y endDate
 * del filtro son los dos extremos de un rango sobre la MISMA columna (startDate),
 * no dos columnas distintas. Es el punto donde más fácil es equivocarse.
 */
class ExecutionPackageCriteriaSearchIT extends AbstractCriteriaSearchIT {

    @Autowired
    private ExecutionPackageCriteriaSearchRepository repository;

    private ComercialEntityType comercialEntityType;

    @BeforeEach
    void seed() {
        BusinessEntity acme = company("ACME OBRAS");
        BusinessEntity globex = company("GLOBEX");

        em.persist(executionPackage("ENERO", LocalDate.of(2026, 1, 15), true, acme));
        em.persist(executionPackage("JUNIO", LocalDate.of(2026, 6, 15), true, globex));
        em.persist(executionPackage("DICIEMBRE", LocalDate.of(2026, 12, 15), true, acme));
        em.persist(executionPackage("CANCELADO", LocalDate.of(2026, 6, 1), false, globex));

        flushAndClear();
    }

    @Test
    void shouldFilterByStartDateRange() {
        assertThat(names(searchPackages(Map.of(
                "startDate", "2026-03-01",
                "endDate", "2026-09-30"))))
                .containsExactlyInAnyOrder("JUNIO", "CANCELADO");
    }

    @Test
    void shouldFilterByOnlyTheLowerBound() {
        assertThat(names(searchPackages(Map.of("startDate", "2026-07-01"))))
                .containsExactly("DICIEMBRE");
    }

    @Test
    void shouldFilterByOnlyTheUpperBound() {
        assertThat(names(searchPackages(Map.of("endDate", "2026-02-01"))))
                .containsExactly("ENERO");
    }

    @Test
    void shouldIncludeTheBoundariesOfTheRange() {
        // El rango es cerrado por ambos extremos: un paquete que empieza justo
        // en la fecha límite debe salir.
        assertThat(names(searchPackages(Map.of(
                "startDate", "2026-06-15",
                "endDate", "2026-06-15"))))
                .containsExactly("JUNIO");
    }

    @Test
    void shouldFilterByEnabledFlag() {
        assertThat(names(searchPackages(Map.of("enabled", false))))
                .containsExactly("CANCELADO");
    }

    @Test
    void shouldFilterByAssociatedCompanyName() {
        assertThat(names(searchPackages(Map.of("companyName", "acme"))))
                .containsExactlyInAnyOrder("ENERO", "DICIEMBRE");
    }

    @Test
    void shouldMatchSearchTextAgainstCompanyName() {
        assertThat(names(searchPackages(Map.of("searchText", "globex"))))
                .containsExactlyInAnyOrder("JUNIO", "CANCELADO");
    }

    private Page<ExecutionPackage> searchPackages(Map<String, Object> filters) {
        return repository.criteriaSearchWithChildren(ExecutionPackage.class, search(filters), em, Map.of());
    }

    private List<String> names(Page<ExecutionPackage> page) {
        return page.getContent().stream().map(ExecutionPackage::getName).toList();
    }

    private BusinessEntity company(String name) {
        BusinessEntity entity = new BusinessEntity();
        entity.setName(name);
        entity.setCode(name.substring(0, 3));
        entity.setIdentificationNumber("ID-" + name.substring(0, 3));
        // comercial_entity_type_id es NOT NULL en base de datos
        entity.setComercialEntityType(comercialEntityType());
        em.persist(entity);
        return entity;
    }

    /**
     * Un unico LOV compartido por todas las empresas del seed: el tipo comercial
     * no interviene en ninguna busqueda de este test, solo hace falta para
     * satisfacer la restriccion NOT NULL.
     */
    private ComercialEntityType comercialEntityType() {
        if (comercialEntityType == null) {
            comercialEntityType = new ComercialEntityType();
            comercialEntityType.setCode("EMP");
            comercialEntityType.setDescription("Empresa constructora");
            comercialEntityType.setEnabled(true);
            em.persist(comercialEntityType);
        }

        return comercialEntityType;
    }

    private ExecutionPackage executionPackage(String name, LocalDate startDate,
                                              boolean enabled, BusinessEntity company) {
        ExecutionPackage entity = new ExecutionPackage();
        entity.setName(name);
        entity.setInitialPackage(false);
        entity.setLength(1000L);
        entity.setStartDate(startDate);
        entity.setEndDate(startDate.plusMonths(1));
        entity.setEnabled(enabled);
        entity.setCompany(company);
        return entity;
    }
}
