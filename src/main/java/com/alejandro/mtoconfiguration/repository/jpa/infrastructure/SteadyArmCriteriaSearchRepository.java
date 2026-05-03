package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CriteriaSearchRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Map;

public interface SteadyArmCriteriaSearchRepository extends
        CriteriaSearchRepository<SteadyArm>, JpaRepository<SteadyArm, Long> {

    @Override
    default Predicate buildPredicate(CriteriaBuilder criteriaBuilder, Root<SteadyArm> root, Map<String, Object> filters, Map<String, Object> params) {
        return null;
    }

    @Override
    default <B extends BaseEntity> Path<B> getSortPath(EntityManager entityManager, Root<SteadyArm> entityRoot, String sortBy) {
        return null;
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
