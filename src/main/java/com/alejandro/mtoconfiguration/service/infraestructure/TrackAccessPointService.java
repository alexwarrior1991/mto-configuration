package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.TrackAccessPointBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.infrastructure.TrackAccessPoint;
import com.alejandro.mtoconfiguration.mapper.infraestructure.TrackAccessPointMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackAccessPointDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.TrackAccessPointFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackAccessPointCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackAccessPointRepository;
import com.alejandro.mtoconfiguration.service.commons.CRUDService;
import com.alejandro.mtoconfiguration.validator.infrastructure.TrackAccessPointValidator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Puntos de acceso a la vía (V28). Alta, modificación, lotes, detalle y borrado lógico son los de
 * {@link CRUDService}, y cada escritura publica su evento {@code track-access-point}; aquí solo van
 * la lista filtrada y los de una vía.
 */
@Service
@RequiredArgsConstructor
public class TrackAccessPointService extends CRUDService<TrackAccessPointDTO, TrackAccessPoint>
        implements ITrackAccessPointService {

    private final TrackAccessPointRepository repository;
    private final TrackAccessPointMapper mapper;
    private final TrackAccessPointValidator validator;
    private final TrackAccessPointBusiness business;
    private final TrackAccessPointCriteriaSearchRepository criteriaSearchRepository;

    @Override
    protected TrackAccessPointMapper getMapper() {
        return mapper;
    }

    @Override
    protected TrackAccessPointValidator getValidator() {
        return validator;
    }

    @Override
    public TrackAccessPoint getEntity() {
        return new TrackAccessPoint();
    }

    @Override
    public TrackAccessPointDTO getDTO() {
        return new TrackAccessPointDTO();
    }

    @Override
    protected TrackAccessPointRepository getRepository() {
        return repository;
    }

    @Override
    protected TrackAccessPointCriteriaSearchRepository getCriteriaSearchRepository() {
        return criteriaSearchRepository;
    }

    @Override
    protected Map<String, Object> searchParams() {
        return Map.of();
    }

    @Override
    protected TrackAccessPointBusiness getBusiness() {
        return business;
    }

    /**
     * La lista de accesos con sus filtros, como {@link Specification}, igual que la de aisladores: la
     * estación es opcional, y con el join interno implícito de QueryDSL un acceso sin estación
     * desaparecería de la búsqueda por texto aunque casara por su nombre. La estación y la vía van con
     * join externo, y Spring Data ordena por ellas ({@code station.name}, {@code track.name}) con otro
     * join externo, que reutiliza estos. Un texto en blanco no filtra; los demás filtros, solo si vienen.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<TrackAccessPointDTO> getTrackAccessPoints(Pageable pageable, TrackAccessPointFilter filter) {
        log.info("Consultando puntos de acceso a la via con filtros funcionales");
        return getRepository().findAll(matching(filter), pageable)
                .map(getMapper()::toDTO);
    }

    static Specification<TrackAccessPoint> matching(TrackAccessPointFilter filter) {
        return (root, query, cb) -> {
            Join<TrackAccessPoint, Track> track = root.join("track", JoinType.LEFT);
            Join<TrackAccessPoint, Station> station = root.join("station", JoinType.LEFT);
            List<Predicate> conditions = new ArrayList<>();

            contains(cb, root.get("name"), filter.name()).ifPresent(conditions::add);
            contains(cb, track.get("name"), filter.trackName()).ifPresent(conditions::add);
            contains(cb, station.get("name"), filter.stationName()).ifPresent(conditions::add);
            Optional.ofNullable(filter.trackId())
                    .map(trackId -> cb.equal(track.get("id"), trackId))
                    .ifPresent(conditions::add);
            Optional.ofNullable(filter.kind())
                    .map(kind -> cb.equal(root.get("kind"), kind))
                    .ifPresent(conditions::add);
            Optional.ofNullable(filter.side())
                    .map(side -> cb.equal(root.get("side"), side))
                    .ifPresent(conditions::add);
            Optional.ofNullable(filter.roadRailAccess())
                    .map(roadRailAccess -> cb.equal(root.get("roadRailAccess"), roadRailAccess))
                    .ifPresent(conditions::add);
            Optional.ofNullable(filter.enabled())
                    .map(enabled -> cb.equal(root.get("enabled"), enabled))
                    .ifPresent(conditions::add);

            // Busqueda general (searchText): el nombre, la via o la estacion.
            if (!filter.searchText().isBlank()) {
                conditions.add(cb.or(Stream.of(
                                contains(cb, root.get("name"), filter.searchText()),
                                contains(cb, track.get("name"), filter.searchText()),
                                contains(cb, station.get("name"), filter.searchText()))
                        .flatMap(Optional::stream)
                        .toArray(Predicate[]::new)));
            }

            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }

    /** Contiene el texto, sin distinguir mayusculas, como {@code JoinPredicates}; en blanco no filtra. */
    private static Optional<Predicate> contains(CriteriaBuilder cb, Expression<String> value, String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(cb.like(cb.upper(value), "%" + text.toUpperCase(Locale.ROOT) + "%"));
    }

    /** Los accesos de una vía, por KP; sin vía, ninguno. */
    @Override
    @Transactional(readOnly = true)
    public List<TrackAccessPointDTO> getTrackAccessPointsByTrackId(Long trackId) {
        return Optional.ofNullable(trackId)
                .map(repository::findByTrackIdOrderByKpAscIdAsc)
                .map(list -> list.stream().map(getMapper()::toDTO).toList())
                .orElseGet(List::of);
    }

    /**
     * No se cachea, aunque su DTO no embebe hijos: lleva decimales (el KP, las toneladas y las
     * coordenadas), que la caché no sabe leer (ver {@code cache.md}), y pasarlos a texto solo para
     * cachear una lista que se lee poco no compensa. {@code CacheableServicesTest} cuenta con ello.
     */
    @Override
    public boolean isCacheable() {
        return false;
    }
}
