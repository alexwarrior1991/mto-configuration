package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;
import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ExecutionPackageCriteriaSearchRepository extends
        CriteriaSearchRepository<ExecutionPackage>, JpaRepository<ExecutionPackage, Long> {

    @Override
    default Predicate buildPredicate(CriteriaBuilder cb, Root<ExecutionPackage> root, Map<String, Object> filters, Map<String, Object> params) {
        if (MapUtils.isEmpty(filters)) {
            return null;
        }

        PredicateBuilder<ExecutionPackage, ExecutionPackage> b = new PredicateBuilder<>(cb, root, filters);

        return b.and(
                b.like("name"),
                b.eq("enabled"),
                b.eq("initialPackage"),
                b.localDateGe("startDate", "startDate"),
                b.localDateLe("startDate", "endDate"),
                JoinPredicates.like(cb, root, filters, "companyName", "company", "name"),
                b.or(
                        b.search("name"),
                        JoinPredicates.searchText(cb, root, filters, "company", "name")
                )
        );
    }

    @Override
    default <B extends BaseEntity> Path<B> getSortPath(EntityManager entityManager, Root<ExecutionPackage> entityRoot, String sortBy) {
        return SortPaths.resolve(entityRoot, sortBy, Set.of(
                "name", "startDate", "endDate", "length", "enabled",
                "createDate", "versionDate", "company.name"));
    }

    @Override
    default int getMaxNumberOfChildPerParent() {
        return 100;   // tracks + stations por paquete
    }

    default void keepValueOrNullValueIfEmptyList(Map<String, Object> filters, String key) {
        filters.computeIfPresent(key, (k, v) ->
                v instanceof List && ((List<?>) v).isEmpty() ? null : v
        );
    }
}
