package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
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

public interface CantileverCriteriaSearchRepository extends
        CriteriaSearchRepository<Cantilever>, JpaRepository<Cantilever, Long> {

    @Override
    default Predicate buildPredicate(CriteriaBuilder cb, Root<Cantilever> root, Map<String, Object> filters, Map<String, Object> params) {
        if (MapUtils.isEmpty(filters)) {
            return null;
        }

        PredicateBuilder<Cantilever, Cantilever> b = new PredicateBuilder<>(cb, root, filters);


        return b.and(
                JoinPredicates.eq(cb, root, filters, "profileId", "profile", "id"),
                JoinPredicates.like(cb, root, filters, "profileCode", "profile", "profileId"),
                JoinPredicates.like(cb, root, filters, "cantileverTypeCode", "cantileverType", "code"),
                b.numberGe("cwHeight", "cwHeightMin"),
                b.numberLe("cwHeight", "cwHeightMax"),
                b.numberGe("stagger", "staggerMin"),
                b.numberLe("stagger", "staggerMax"),
                b.or(
                        JoinPredicates.searchText(cb, root, filters, "profile", "profileId"),
                        JoinPredicates.searchText(cb, root, filters, "cantileverType", "code")
                )
        );
    }

    @Override
    default <B extends BaseEntity> Path<B> getSortPath(EntityManager entityManager, Root<Cantilever> entityRoot, String sortBy) {
        return SortPaths.resolve(entityRoot, sortBy, Set.of(
                "cwHeight", "stagger", "catenaryHeight", "cwElevation", "windDeflection", "armAngle",
                "createDate", "versionDate", "profile.profileId", "cantileverType.code"));
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
