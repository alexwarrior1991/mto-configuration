package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
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

public interface StationCriteriaSearchRepository extends
        CriteriaSearchRepository<Station>, JpaRepository<Station, Long> {

    @Override
    default Predicate buildPredicate(CriteriaBuilder cb, Root<Station> root, Map<String, Object> filters, Map<String, Object> params) {
        if (MapUtils.isEmpty(filters)) {
            return null;
        }

        PredicateBuilder<Station, Station> b = new PredicateBuilder<>(cb, root, filters);

        return b.and(
                b.like("name"),
                JoinPredicates.like(cb, root, filters, "executionPackageName", "executionPackage", "name"),
                JoinPredicates.like(cb, root, filters, "trackName", "tracks", "name"),
                b.or(
                        b.search("name"),
                        JoinPredicates.searchText(cb, root, filters, "executionPackage", "name")
                )
        );
    }

    @Override
    default <B extends BaseEntity> Path<B> getSortPath(EntityManager entityManager, Root<Station> entityRoot, String sortBy) {
        return SortPaths.resolve(entityRoot, sortBy, Set.of(
                "name", "createDate", "versionDate", "executionPackage.name"));
    }

    @Override
    default int getMaxNumberOfChildPerParent() {
        return 50;
    }

    default void keepValueOrNullValueIfEmptyList(Map<String, Object> filters, String key) {
        filters.computeIfPresent(key, (k, v) ->
                v instanceof List && ((List<?>) v).isEmpty() ? null : v
        );
    }
}
