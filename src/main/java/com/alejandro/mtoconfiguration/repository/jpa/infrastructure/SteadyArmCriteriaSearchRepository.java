package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
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

public interface SteadyArmCriteriaSearchRepository extends
        CriteriaSearchRepository<SteadyArm>, JpaRepository<SteadyArm, Long> {

    @Override
    default Predicate buildPredicate(CriteriaBuilder cb, Root<SteadyArm> root, Map<String, Object> filters, Map<String, Object> params) {
        if (MapUtils.isEmpty(filters)) {
            return null;
        }

        PredicateBuilder<SteadyArm, SteadyArm> b = new PredicateBuilder<>(cb, root, filters);

        return b.and(
                JoinPredicates.eq(cb, root, filters, "cantileverId", "cantilever", "id"),
                JoinPredicates.like(cb, root, filters, "steadyArmTypeCode", "steadyArmType", "code"),
                b.numberGe("length", "lengthMin"),
                b.numberLe("length", "lengthMax"),
                b.or(
                        b.searchNumeric("length"),
                        JoinPredicates.searchText(cb, root, filters, "steadyArmType", "code")
                )
        );
    }

    @Override
    default <B extends BaseEntity> Path<B> getSortPath(EntityManager entityManager, Root<SteadyArm> entityRoot, String sortBy) {
        return SortPaths.resolve(entityRoot, sortBy, Set.of(
                "length", "createDate", "versionDate", "steadyArmType.code"));
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
