package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
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

public interface TrackCriteriaSearchRepository extends
        CriteriaSearchRepository<Track>, JpaRepository<Track, Long> {

    @Override
    default Predicate buildPredicate(CriteriaBuilder cb, Root<Track> root, Map<String, Object> filters, Map<String, Object> params) {
        if (MapUtils.isEmpty(filters)) {
            return null;
        }

        PredicateBuilder<Track, Track> b = new PredicateBuilder<>(cb, root, filters);

        return b.and(
                b.like("name"),
                b.eq("enabled"),
                JoinPredicates.like(cb, root, filters, "executionPackageName", "executionPackage", "name"),
                JoinPredicates.like(cb, root, filters, "stationName", "station", "name"),
                b.or(
                        b.search("name"),
                        JoinPredicates.searchText(cb, root, filters, "executionPackage", "name"),
                        JoinPredicates.searchText(cb, root, filters, "station", "name")
                )

        );
    }

    @Override
    default <B extends BaseEntity> Path<B> getSortPath(EntityManager entityManager, Root<Track> entityRoot, String sortBy) {
        return SortPaths.resolve(entityRoot, sortBy, Set.of(
                "name", "enabled", "createDate", "versionDate",
                "executionPackage.name", "station.name"));
    }

    @Override
    default int getMaxNumberOfChildPerParent() {
        return CriteriaSearchRepository.super.getMaxNumberOfChildPerParent();
    }

    default void keepValueOrNullValueIfEmptyList(Map<String, Object> filters, String key) {
        filters.computeIfPresent(key, (k, v) ->
                v instanceof List && ((List<?>) v).isEmpty() ? null : v
        );
    }
}
