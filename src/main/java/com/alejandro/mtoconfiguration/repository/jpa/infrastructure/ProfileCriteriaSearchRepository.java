package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.commons.JoinPredicates;
import com.alejandro.mtoconfiguration.repository.jpa.commons.PredicateBuilder;
import com.alejandro.mtoconfiguration.repository.jpa.commons.SortPaths;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ProfileCriteriaSearchRepository extends
        CriteriaSearchRepository<Profile>, JpaRepository<Profile, Long> {

    @Override
    default Predicate buildPredicate(CriteriaBuilder cb, Root<Profile> root, Map<String, Object> filters, Map<String, Object> params) {
        if (MapUtils.isEmpty(filters)) {
            return null;
        }

        PredicateBuilder<Profile, Profile> b = new PredicateBuilder<>(cb, root, filters);

        return b.and(
                b.like("profileId"),
                b.eq("kp"),
                b.numberFrom("kp"),          // claves kpFrom / kpTo si el front las manda
                b.numberTo("kp"),
                JoinPredicates.eq(cb, root, filters, "trackId", "track", "id"),
                JoinPredicates.like(cb, root, filters, "trackName", "track", "name"),
                JoinPredicates.like(cb, root, filters, "anchorageCode", "anchorage", "code"),
                JoinPredicates.like(cb, root, filters, "anchorageFoundationCode", "anchorageFoundation", "code"),
                JoinPredicates.like(cb, root, filters, "foundationCode", "foundation", "code"),
                JoinPredicates.like(cb, root, filters, "poleTypeCode", "poleType", "code"),
                JoinPredicates.like(cb, root, filters, "portalCode", "portal", "code"),
                JoinPredicates.like(cb, root, filters, "profileStatusCode", "profileStatus", "code"),
                JoinPredicates.like(cb, root, filters, "returnSupportCode", "returnSupport", "code"),
                JoinPredicates.like(cb, root, filters, "sectioningCode", "sectioning", "code"),
                stationNamePredicate(cb, root, filters),
                b.or(
                        b.search("profileId"),
                        b.searchNumeric("kp"),
                        JoinPredicates.searchText(cb, root, filters, "track", "name")
                )
        );

    }

    /**
     * stationName cuelga de track.station: dos saltos, así que no lo cubre
     * JoinPredicates.like, que sólo navega una asociación.
     */
    private static Predicate stationNamePredicate(CriteriaBuilder cb, Root<Profile> root,
                                                  Map<String, Object> filters) {
        String value = MapUtils.getString(filters, "stationName");

        if (StringUtils.isBlank(value)) {
            return null;
        }

        Join<Profile, Track> track = root.join("track", JoinType.LEFT);
        Join<Track, Station> station = track.join("station", JoinType.LEFT);

        return cb.like(cb.upper(station.get("name")), "%" + value.toUpperCase() + "%");
    }

    @Override
    default <B extends BaseEntity> Path<B> getSortPath(EntityManager entityManager, Root<Profile> entityRoot, String sortBy) {
        return SortPaths.resolve(entityRoot, sortBy, Set.of(
                "profileId", "kp", "createDate", "versionDate",
                "track.name", "profileStatus.code", "poleType.code"));
    }

    @Override
    default int getMaxNumberOfChildPerParent() {
        return 20;   // cantilevers por perfil
    }

    default void keepValueOrNullValueIfEmptyList(Map<String, Object> filters, String key) {
        filters.computeIfPresent(key, (k, v) ->
                v instanceof List && ((List<?>) v).isEmpty() ? null : v
        );
    }
}
