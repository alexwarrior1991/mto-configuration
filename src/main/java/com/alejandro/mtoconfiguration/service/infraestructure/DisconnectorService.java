package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.DisconnectorBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.lov.DisconnectorFunction;
import com.alejandro.mtoconfiguration.mapper.infraestructure.DisconnectorMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.DisconnectorFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.service.commons.CRUDService;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.utils.InfrastructureUtils;
import com.alejandro.mtoconfiguration.validator.infrastructure.DisconnectorValidator;
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

@Service
@RequiredArgsConstructor
public class DisconnectorService extends CRUDService<DisconnectorDTO, Disconnector> implements
        IDisconnectorService {

    private final DisconnectorRepository repository;
    private final DisconnectorMapper mapper;
    private final DisconnectorValidator validator;
    private final DisconnectorBusiness business;
    private final DisconnectorCriteriaSearchRepository criteriaSearchRepository;
    private final MasterDataService masterDataService;
    private InfrastructureUtils infrastructureUtils;


    @Override
    protected DisconnectorMapper getMapper() {
        return mapper;
    }

    @Override
    protected DisconnectorValidator getValidator() {
        return validator;
    }

    @Override
    public Disconnector getEntity() {
        return new Disconnector();
    }

    @Override
    public DisconnectorDTO getDTO() {
        return new DisconnectorDTO();
    }

    @Override
    protected DisconnectorRepository getRepository() {
        return repository;
    }

    @Override
    protected DisconnectorCriteriaSearchRepository getCriteriaSearchRepository() {
        return criteriaSearchRepository;
    }

    @Override
    protected Map<String, Object> searchParams() {
        return Map.of();
    }

    @Override
    protected DisconnectorBusiness getBusiness() {
        return business;
    }

    /**
     * La lista de seccionadores con sus filtros, como {@link Specification} y no como predicado de
     * QueryDSL: la estación es opcional, y QueryDSL llegaba a ella en el {@code WHERE} con un join
     * implícito, que es interno. Un seccionador sin estación desaparecía de la búsqueda por texto
     * aunque casara por su nombre. Aquí la estación y la función van con join externo, y Spring Data
     * ordena por una asociación opcional ({@code station.name}, {@code profile.profileId}) con otro
     * join externo, que reutiliza estos. Un filtro en blanco no filtra, y {@code onLoad} solo si viene.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<DisconnectorDTO> getDisconnectors(Pageable pageable, DisconnectorFilter filter) {
        log.info("Consultando seccionadores con filtros funcionales");
        return getRepository().findAll(matching(filter), pageable)
                .map(getMapper()::toDTO);
    }

    static Specification<Disconnector> matching(DisconnectorFilter filter) {
        return (root, query, cb) -> {
            Join<Disconnector, Station> station = root.join("station", JoinType.LEFT);
            Join<Disconnector, DisconnectorFunction> function = root.join("disconnectorFunction", JoinType.LEFT);
            List<Predicate> conditions = new ArrayList<>();

            contains(cb, root.get("name"), filter.name()).ifPresent(conditions::add);
            contains(cb, station.get("name"), filter.stationName()).ifPresent(conditions::add);
            contains(cb, function.get("description"), filter.functionName()).ifPresent(conditions::add);
            Optional.ofNullable(filter.onLoad())
                    .map(onLoad -> cb.equal(root.get("onLoad"), onLoad))
                    .ifPresent(conditions::add);

            // Busqueda general (searchText): el nombre, la estacion o la funcion.
            if (!filter.searchText().isBlank()) {
                conditions.add(cb.or(Stream.of(
                                contains(cb, root.get("name"), filter.searchText()),
                                contains(cb, station.get("name"), filter.searchText()),
                                contains(cb, function.get("description"), filter.searchText()))
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

    @Override
    @Transactional(readOnly = true)
    public List<DisconnectorDTO> getDisconnectorByStationId(Long stationId) {
        return Optional.ofNullable(stationId)
                .map(repository::findByStationId)
                .map(list -> list.stream().map(mapper::toDTO).toList())
                .orElseGet(List::of);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisconnectorDTO> getDisconnectorsByStationName(String stationName) {
        return Optional.ofNullable(stationName)
                .filter(name -> !name.isBlank())
                .map(repository::findByStationNameContainingIgnoreCase)
                .map(list -> list.stream().map(mapper::toDTO).toList())
                .orElseGet(List::of);
    }

    /**
     * DisconnectorDTO no embebe otros DTO de entidad, solo LOV, que se editan casi nunca, asi
     * que si compensa cachearlas. Pero no es del todo independiente: {@code profileCode} y
     * {@code profileKp} se copian del perfil, y la fila se escribe tambien anidada en un perfil
     * (1:1) o en una estacion, con lo que una escritura de {@code ProfileService},
     * {@code TrackService}, {@code StationService} o {@code ExecutionPackageService} la deja
     * obsoleta. {@code CacheEvictionListener.DEPENDENT_SERVICES} vacia esta cache con esos
     * eventos. Ver BaseService.isCacheable().
     */
    @Override
    public boolean isCacheable() {
        return true;
    }
}
