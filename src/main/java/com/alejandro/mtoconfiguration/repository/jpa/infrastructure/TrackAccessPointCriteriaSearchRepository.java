package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.TrackAccessPoint;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointKind;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointSide;
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

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public interface TrackAccessPointCriteriaSearchRepository extends
        CriteriaSearchRepository<TrackAccessPoint>, JpaRepository<TrackAccessPoint, Long> {

    @Override
    default Predicate buildPredicate(CriteriaBuilder cb, Root<TrackAccessPoint> root, Map<String, Object> filters,
                                     Map<String, Object> params) {
        if (MapUtils.isEmpty(filters)) {
            return null;
        }

        PredicateBuilder<TrackAccessPoint, TrackAccessPoint> b = new PredicateBuilder<>(cb, root, filters);

        return b.and(
                b.like("name"),
                b.eq("enabled"),
                b.eq("roadRailAccess"),
                enumEquals(cb, root, filters, "kind", TrackAccessPointKind::fromCode),
                enumEquals(cb, root, filters, "side", TrackAccessPointSide::fromCode),
                JoinPredicates.eq(cb, root, filters, "trackId", "track", "id"),
                JoinPredicates.like(cb, root, filters, "trackName", "track", "name"),
                JoinPredicates.like(cb, root, filters, "stationName", "station", "name"),
                b.or(
                        b.search("name"),
                        JoinPredicates.searchText(cb, root, filters, "track", "name"),
                        JoinPredicates.searchText(cb, root, filters, "station", "name")
                )
        );
    }

    /**
     * Un enumerado se compara como tal, no como texto, igual que el tipo de instalación del aislador
     * ({@code SectionInsulatorCriteriaSearchRepository}): el filtro llega desde JSON como
     * {@code String}, y un valor que no existe filtra por «ninguno» en lugar de reventar la búsqueda.
     */
    private static <V extends Enum<V>> Predicate enumEquals(CriteriaBuilder cb,
                                                            Root<TrackAccessPoint> root,
                                                            Map<String, Object> filters,
                                                            String key,
                                                            Function<String, V> fromCode) {
        Object raw = filters.get(key);

        if (raw == null) {
            return null;
        }

        V value = raw instanceof Enum<?> ? fromCode.apply(((Enum<?>) raw).name()) : fromCode.apply(raw.toString());

        return value == null
                ? cb.disjunction()
                : cb.equal(root.get(key), value);
    }

    @Override
    default <B extends BaseEntity> Path<B> getSortPath(EntityManager entityManager, Root<TrackAccessPoint> entityRoot,
                                                       String sortBy) {
        return SortPaths.resolve(entityRoot, sortBy, Set.of(
                "name", "enabled", "kp", "kind", "side", "roadRailAccess", "createDate", "versionDate",
                "track.name", "station.name"));
    }
}
