package com.alejandro.mtoconfiguration.repository.jpa.commons;

import jakarta.persistence.criteria.*;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

/**
 * Resuelve la ruta de ordenación validándola contra una lista blanca.
 * sortBy es entrada del usuario: sin lista blanca se puede ordenar por
 * cualquier columna interna o tumbar la consulta con una ruta inexistente.
 */
public final class SortPaths {

    private SortPaths() {
    }

    @SuppressWarnings("unchecked")
    public static <B, E> Path<B> resolve(Root<E> root, String sortBy, Set<String> allowed){
        if (StringUtils.isBlank(sortBy) || !allowed.contains(sortBy)) {
            return null; // el llamante cae al orden por defecto
        }

        if (!sortBy.contains(".")){
            return (Path<B>) root.get(sortBy);
        }


        String[] parts = sortBy.split("\\.", 2);
        Join<?, ?> join = joinOnce(root, parts[0]);
        return (Path<B>) join.get(parts[1]);
    }

    private static Join<?, ?> joinOnce(From<?, ?> from, String association) {
        return from.getJoins().stream()
                .filter(join -> join.getAttribute().getName().equals(association))
                .findFirst()
                .map(join -> (Join<?, ?>) join)
                .orElseGet(() -> from.join(association, JoinType.LEFT));
    }


}
