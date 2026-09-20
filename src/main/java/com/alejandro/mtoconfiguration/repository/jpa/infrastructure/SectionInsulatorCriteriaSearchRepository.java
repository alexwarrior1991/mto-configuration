package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.enums.infrastructure.SectionInsulatorInstallationType;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.commons.JoinPredicates;
import com.alejandro.mtoconfiguration.repository.jpa.commons.PredicateBuilder;
import com.alejandro.mtoconfiguration.repository.jpa.commons.SortPaths;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.apache.commons.collections4.MapUtils;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface SectionInsulatorCriteriaSearchRepository extends
        CriteriaSearchRepository<SectionInsulator>, JpaRepository<SectionInsulator, Long> {

    @Override
    default Predicate buildPredicate(CriteriaBuilder cb, Root<SectionInsulator> root, Map<String, Object> filters, Map<String, Object> params) {
        if (MapUtils.isEmpty(filters)) {
            return null;
        }

        PredicateBuilder<SectionInsulator, SectionInsulator> b = new PredicateBuilder<>(cb, root, filters);

        return b.and(
                b.like("name"),
                b.eq("enabled"),
                installationTypeEquals(cb, root, filters),
                JoinPredicates.like(cb, root, filters, "stationName", "station", "name"),
                JoinPredicates.like(cb, root, filters, "trackName", "track", "name"),
                JoinPredicates.like(cb, root, filters, "switchCode", "switches", "code"),
                b.or(
                        b.search("name"),
                        JoinPredicates.searchText(cb, root, filters, "station", "name"),
                        JoinPredicates.searchText(cb, root, filters, "track", "name"),
                        JoinPredicates.searchText(cb, root, filters, "switches", "code")
                )
        );
    }

    /**
     * El tipo de instalación se compara como enum, no como texto.
     *
     * <p>El filtro llega desde JSON, así que el valor del mapa es un {@code String}. Pasárselo tal
     * cual a {@code criteriaBuilder.equal} contra una ruta de tipo enum deja la comparación a
     * merced de cómo resuelva Hibernate el literal; convertirlo aquí la hace explícita. Y usa la
     * lectura tolerante: un valor que no existe filtra por «ninguno» en lugar de reventar la
     * búsqueda con un {@code IllegalArgumentException}.
     */
    private static Predicate installationTypeEquals(CriteriaBuilder cb,
                                                    Root<SectionInsulator> root,
                                                    Map<String, Object> filters) {

        Object raw = filters.get("installationType");

        if (raw == null) {
            return null;
        }

        SectionInsulatorInstallationType value = raw instanceof SectionInsulatorInstallationType type
                ? type
                : SectionInsulatorInstallationType.fromCode(raw.toString());

        return value == null
                ? cb.disjunction()
                : cb.equal(root.get("installationType"), value);
    }

    @Override
    default <B extends BaseEntity> Path<B> getSortPath(EntityManager entityManager, Root<SectionInsulator> entityRoot, String sortBy) {
        return SortPaths.resolve(entityRoot, sortBy, Set.of(
                "name", "enabled", "kp", "installationType", "createDate", "versionDate",
                "station.name", "track.name"));
    }

    /**
     * Ya no es 1: el filtro por código de aguja hace un JOIN a la colección de agujas, y un
     * aislador con tres agujas devuelve tres filas en la consulta de ids.
     *
     * <p>Esa consulta pagina ANTES de deduplicar (ver {@code CriteriaSearchRepository}), así que sin
     * sobre-leer una página de 20 podría traer 7 aisladores. Ocho es holgado para lo que enseña el
     * plano —dos agujas por aislador, alguna vez tres— con el mismo criterio con el que el perfil
     * declara 20 por sus ménsulas.
     */
    @Override
    default int getMaxNumberOfChildPerParent() {
        return 8;   // agujas por aislador
    }

    default void keepValueOrNullValueIfEmptyList(Map<String, Object> filters, String key) {
        filters.computeIfPresent(key, (k, v) ->
                v instanceof List && ((List<?>) v).isEmpty() ? null : v
        );
    }
}
