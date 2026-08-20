package com.alejandro.mtoconfiguration.repository.jpa.commons;


import jakarta.persistence.criteria.*;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * Predicados sobre asociaciones. El join se crea sólo cuando el filtro viene
 * informado, para que una búsqueda sin filtros no arrastre LEFT JOIN inútiles.
 */
public final class JoinPredicates {

    private JoinPredicates(){
    }

    /** LIKE case-insensitive sobre una columna de la entidad asociada. */
    public static Predicate like(CriteriaBuilder cb, From<?, ?> from, Map<String, Object> filters,
                                 String filterKey, String association, String column){

        String value = MapUtils.getString(filters, filterKey);

        if (StringUtils.isBlank(value)){
            return null;
        }

        Join<?, ?> join = joinOnce(from, association);
        return cb.like(cb.upper(join.get(column)), "%" + value.toUpperCase() + "%");
    }

    /** Igualdad sobre una columna de la entidad asociada (típicamente el id). */
    public static Predicate eq(CriteriaBuilder cb, From<?, ?> from, Map<String, Object> filters,
                               String filterKey, String association, String column) {

        Object value = MapUtils.getObject(filters, filterKey);

        if (value == null || (value instanceof String s && StringUtils.isBlank(s))) {
            return null;
        }

        Join<?, ?> join = joinOnce(from, association);
        return cb.equal(join.get(column), value);
    }

    /**
     * LIKE del searchText sobre una columna asociada, para incluirla en la búsqueda libre.
     * Devuelve null si no hay searchText, de modo que se puede pasar a
     * PredicateBuilder.or(...) sin comprobaciones previas.
     */
    public static Predicate searchText(CriteriaBuilder cb, From<?, ?> from, Map<String, Object> filters,
                                       String association, String column) {

        String value = StringUtils.defaultIfBlank(
                MapUtils.getString(filters, "search"),
                MapUtils.getString(filters, "searchText"));

        if (StringUtils.isBlank(value)) {
            return null;
        }

        Join<?, ?> join = joinOnce(from, association);
        return cb.like(cb.upper(join.get(column)), "%" + value.toUpperCase() + "%");
    }


    /**
     * Reutiliza el join si ya se creó para otro filtro. Sin esto, filtrar por
     * stationName y buscar por searchText en la misma columna generaría dos JOIN
     * a la misma tabla y el producto cartesiano correspondiente.
     */
    private static Join<?, ?> joinOnce(From<?, ?> from, String association) {
        return from.getJoins().stream()
                .filter(join -> join.getAttribute().getName().equals(association))
                .findFirst()
                .map(join -> (Join<?, ?>) join)
                .orElseGet(() -> from.join(association, JoinType.LEFT));
    }


}
