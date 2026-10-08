package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.SectionInsulatorBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulatorSwitch;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.infraestructure.SectionInsulatorMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.SectionInsulatorFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
import com.alejandro.mtoconfiguration.service.commons.CRUDService;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.utils.InfrastructureUtils;
import com.alejandro.mtoconfiguration.utils.PermissionUtils;
import com.alejandro.mtoconfiguration.validator.infrastructure.SectionInsulatorValidator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SectionInsulatorService extends CRUDService<SectionInsulatorDTO, SectionInsulator>
        implements ISectionInsulatorService {

    private final SectionInsulatorRepository repository;
    private final SectionInsulatorMapper mapper;
    private final SectionInsulatorValidator validator;
    private final SectionInsulatorBusiness business;
    private final SectionInsulatorCriteriaSearchRepository criteriaSearchRepository;
    private final MasterDataService masterDataService;
    private final InfrastructureUtils infrastructureUtils;

    @Override
    protected SectionInsulatorMapper getMapper() {
        return mapper;
    }

    @Override
    protected SectionInsulatorValidator getValidator() {
        return validator;
    }

    @Override
    public SectionInsulator getEntity() {
        return new SectionInsulator();
    }

    @Override
    public SectionInsulatorDTO getDTO() {
        return new SectionInsulatorDTO();
    }

    @Override
    protected SectionInsulatorRepository getRepository() {
        return repository;
    }

    @Override
    protected SectionInsulatorCriteriaSearchRepository getCriteriaSearchRepository() {
        return criteriaSearchRepository;
    }

    @Override
    protected Map<String, Object> searchParams() {
        return Optional.of(new HashMap<String, Object>())
                .map(params -> {
                    params.put("userId", PermissionUtils.getUserId());
                    params.put("username", PermissionUtils.getUsername());

                    Optional.ofNullable(PermissionUtils.getCurrentRoles())
                            .filter(roles -> !roles.isEmpty())
                            .ifPresent(roles -> params.put("roles", roles));

                    return params;
                })
                .map(Collections::unmodifiableMap)
                .orElseGet(Map::of);
    }

    @Override
    protected SectionInsulatorBusiness getBusiness() {
        return business;
    }

    /**
     * La lista de aisladores con sus filtros, como {@link Specification} y no como predicado de
     * QueryDSL: la vía de un aislador es opcional (solo la exige {@code TRACK_CONNECTION}), y QueryDSL
     * llegaba a ella en el {@code WHERE} con un join implícito, que es interno. Un aislador sin vía
     * desaparecía de la búsqueda por texto aunque casara por su nombre. Aquí la estación y la vía van
     * con join externo, y Spring Data ordena por una asociación opcional ({@code track.name}) con otro
     * join externo, que reutiliza estos. Las agujas van con un {@code EXISTS}. Un filtro en blanco no
     * filtra, y {@code installationType} y {@code enabled} solo si vienen.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<SectionInsulatorDTO> getSectionInsulators(Pageable pageable, SectionInsulatorFilter filter) {
        log.info("Consultando aisladores de sección con filtros funcionales");
        return getRepository().findAll(matching(filter), pageable)
                .map(getMapper()::toDTO);
    }

    static Specification<SectionInsulator> matching(SectionInsulatorFilter filter) {
        return (root, query, cb) -> {
            Join<SectionInsulator, Station> station = root.join("station", JoinType.LEFT);
            Join<SectionInsulator, Track> track = root.join("track", JoinType.LEFT);
            List<Predicate> conditions = new ArrayList<>();

            contains(cb, root.get("name"), filter.name()).ifPresent(conditions::add);
            contains(cb, station.get("name"), filter.stationName()).ifPresent(conditions::add);
            contains(cb, track.get("name"), filter.trackName()).ifPresent(conditions::add);
            anySwitchContains(root, query, cb, filter.switchCode()).ifPresent(conditions::add);
            Optional.ofNullable(filter.installationType())
                    .map(type -> cb.equal(root.get("installationType"), type))
                    .ifPresent(conditions::add);
            Optional.ofNullable(filter.enabled())
                    .map(enabled -> cb.equal(root.get("enabled"), enabled))
                    .ifPresent(conditions::add);

            // Búsqueda general (searchText): el nombre, la estación, la vía o el código de una aguja.
            if (!filter.searchText().isBlank()) {
                conditions.add(cb.or(Stream.of(
                                contains(cb, root.get("name"), filter.searchText()),
                                contains(cb, station.get("name"), filter.searchText()),
                                contains(cb, track.get("name"), filter.searchText()),
                                anySwitchContains(root, query, cb, filter.searchText()))
                        .flatMap(Optional::stream)
                        .toArray(Predicate[]::new)));
            }

            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }

    /**
     * Alguna de sus agujas contiene el código. Es un {@code EXISTS} y no un join de la colección,
     * porque con el join un aislador con dos agujas que casan saldría dos veces en la página.
     */
    private static Optional<Predicate> anySwitchContains(Root<SectionInsulator> root, CriteriaQuery<?> query,
                                                         CriteriaBuilder cb, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        Subquery<Integer> anySwitch = query.subquery(Integer.class);
        Join<SectionInsulator, SectionInsulatorSwitch> switches = anySwitch.correlate(root).join("switches");
        anySwitch.select(cb.literal(1)).where(like(cb, switches.get("code"), code));
        return Optional.of(cb.exists(anySwitch));
    }

    /** Contiene el texto, sin distinguir mayúsculas, como {@code JoinPredicates}; en blanco no filtra. */
    private static Optional<Predicate> contains(CriteriaBuilder cb, Expression<String> value, String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(like(cb, value, text));
    }

    private static Predicate like(CriteriaBuilder cb, Expression<String> value, String text) {
        return cb.like(cb.upper(value), "%" + text.toUpperCase(Locale.ROOT) + "%");
    }

    @Override
    @Transactional(readOnly = true)
    public List<SectionInsulatorDTO> getSectionInsulatorsByStationId(Long stationId) {
        return Optional.ofNullable(stationId)
                .map(repository::findByStationId)
                .map(list -> list.stream().map(getMapper()::toDTO).toList())
                .orElseGet(List::of);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SectionInsulatorDTO> getSectionInsulatorsByStationName(String stationName) {
        return Optional.ofNullable(stationName)
                .filter(name -> !name.isBlank())
                .map(repository::findByStationNameContainingIgnoreCase)
                .map(list -> list.stream().map(getMapper()::toDTO).toList())
                .orElseGet(List::of);
    }

    /**
     * Ya no se cachea: desde que el aislador lleva agujas, su DTO embebe hijos.
     *
     * <p>Antes valía true porque {@code SectionInsulatorDTO} sólo tenía escalares y una referencia
     * a la estación, de modo que ninguna entrada se quedaba obsoleta al cambiar otra entidad. Con
     * {@code switches} dentro, cualquier alta, modificación o borrado de una aguja deja la entrada
     * cacheada mintiendo, y la caché no sabe nada de la aguja para invalidarla.
     *
     * <p>{@code CacheableServicesTest} comprueba exactamente esta correspondencia: los únicos DTO
     * que se cachean son los que no embeben otro DTO de entidad. Ver {@code BaseService.isCacheable()}.
     */
    @Override
    public boolean isCacheable() {
        return false;
    }
}
