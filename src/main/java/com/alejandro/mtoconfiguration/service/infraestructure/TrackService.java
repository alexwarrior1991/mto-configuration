package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.TrackBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.QTrack;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.infraestructure.TrackMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.TrackFilter;
import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackAccessPointRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackRepository;
import com.alejandro.mtoconfiguration.service.commons.CRUDService;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.utils.InfrastructureUtils;
import com.alejandro.mtoconfiguration.utils.PermissionUtils;
import com.alejandro.mtoconfiguration.validator.infrastructure.TrackValidator;
import com.querydsl.core.BooleanBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class TrackService extends CRUDService<TrackDTO, Track>
        implements ITrackService {

    private final TrackRepository repository;
    private final TrackMapper mapper;
    private final TrackValidator validator;
    private final TrackBusiness business;
    private final TrackCriteriaSearchRepository criteriaSearchRepository;
    private final MasterDataService masterDataService;
    private final InfrastructureUtils infrastructureUtils;
    private final ProfileRepository profileRepository;
    private final DisconnectorRepository disconnectorRepository;
    private final SectionInsulatorRepository sectionInsulatorRepository;
    private final TrackAccessPointRepository trackAccessPointRepository;
    private final DeletionGuard deletionGuard;

    /** Perfiles por lote al cargar la rama de una via que se borra entera, con su grafo de evento. */
    private static final int CASCADE_BATCH_SIZE = 200;
    /** Sentencias iguales por lote JDBC al escribir esa rama; solo en la sesion de ese borrado. */
    private static final int CASCADE_JDBC_BATCH_SIZE = 100;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    protected TrackMapper getMapper() {
        return mapper;
    }

    @Override
    protected TrackValidator getValidator() {
        return validator;
    }

    @Override
    public Track getEntity() {
        return new Track();
    }

    @Override
    public TrackDTO getDTO() {
        return new TrackDTO();
    }

    @Override
    protected TrackRepository getRepository() {
        return repository;
    }

    @Override
    protected TrackCriteriaSearchRepository getCriteriaSearchRepository() {
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
    protected TrackBusiness getBusiness() {
        return business;
    }

    /**
     * Borra la via <b>entera</b>: ella y todo lo que es suyo, en logico y en una sola transaccion.
     * Es {@code DELETE /tracks/{id}?cascade=true}; el {@code DELETE} a secas de una via con algo
     * colgando es un 409 ({@link DeletionGuard}).
     *
     * <p>Lo suyo: sus perfiles con sus mensulas y sus brazos, los seccionadores que estan en ella
     * (en uno de sus postes o, sin poste, con ella de via propia), sus aisladores con sus agujas y
     * sus puntos de acceso. Lo de <b>otra</b> via que la nombra (el seccionador o el aislador que
     * la tiene de via conectada, el que la maniobra desde el poste de otra, la aguja de otro
     * aislador) no se borra ni se cambia: impide el borrado, con un 409 que lo dice. Vaciar esa
     * referencia en silencio dejaria un aislador de conexion sin su segunda via, que su validador
     * exige, y borrarlo seria borrar algo de otra via sin que nadie lo haya pedido.
     *
     * <p>Cada entidad borrada publica su {@code DELETED}, el de la via el primero. La rama se carga
     * por lotes y con el grafo de su evento ({@code findAllByIdForMessaging}), no fila a fila, y se
     * escribe en lotes JDBC. Aun asi son tres filas y tres eventos por perfil con mensula y brazo,
     * y el outbox guarda cada evento con una lectura y una escritura: una via de 3.000 perfiles
     * son unos 10 s contra una base en la misma maquina (README_API.md §1).
     */
    @Override
    @jakarta.transaction.Transactional(rollbackOn = {ValidationException.class, Exception.class})
    public void deleteWithEverything(Long id) throws BaseException {
        trackingNestedChanges(() -> {
            Track track = repository.findById(id).orElseThrow(() -> notFound(id));

            TrackDTO dto = new TrackDTO();
            dto.setId(id);
            Optional.ofNullable(validator.validateBeforeDelete(dto))
                    .filter(alerts -> !alerts.isEmpty())
                    .ifPresent(alerts -> {
                        throw new ValidationException(alerts);
                    });

            // Bloquea la via y su rama y comprueba que nada de otra via apunta a ella (409).
            deletionGuard.requireTrackCanGoWhole(id);

            entityManager.unwrap(Session.class).setJdbcBatchSize(CASCADE_JDBC_BATCH_SIZE);

            List<Long> profileIds = profileRepository.findIdsByTrackId(id);

            for (int from = 0; from < profileIds.size(); from += CASCADE_BATCH_SIZE) {
                // Quedan en la sesion con todo lo que lee su evento: track.getProfiles() ya no
                // carga nada por perfil.
                profileRepository.findAllByIdForMessaging(
                        profileIds.subList(from, Math.min(from + CASCADE_BATCH_SIZE, profileIds.size())));
            }

            List<CRUDEntity> own = new ArrayList<>();
            own.addAll(disconnectorRepository.findAllById(disconnectorRepository.findLiveIdsOnTrack(id)));
            own.addAll(sectionInsulatorRepository.findAllById(sectionInsulatorRepository.findIdsLocatedOnTrack(id)));
            own.addAll(trackAccessPointRepository.findByTrackIdOrderByKpAscIdAsc(id));

            // Por capas y con un flush en cada una: Hibernate solo agrupa en un lote JDBC las
            // sentencias iguales seguidas, y un perfil, su mensula y su brazo, que estan juntos en
            // la sesion, saldrian de uno en uno.
            List<Cantilever> cantilevers = track.getProfiles().stream()
                    .flatMap(profile -> profile.getCantilevers().stream())
                    .distinct()
                    .toList();
            cantilevers.stream().map(Cantilever::getSteadyArm).filter(Objects::nonNull).forEach(CRUDEntity::delete);
            repository.flush();
            cantilevers.forEach(CRUDEntity::delete);
            repository.flush();
            track.getProfiles().forEach(CRUDEntity::delete);
            repository.flush();

            track.deleteCascade();
            own.forEach(CRUDEntity::deleteCascade);

            Track saved = repository.saveAndFlush(track);

            deletionGuard.requireNothingLeftOnTrack(id);
            checkWrittenEntities();
            publishEntityDeletedEvent(saved);
            publishNestedChanges();
            publishCacheEvictionEvent();

            return null;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TrackDTO> getTracks(Pageable pageable, TrackFilter filter) {
        log.info("Consultando vías con filtros funcionales");

        QTrack qEntity = QTrack.track;
        BooleanBuilder builder = new BooleanBuilder();

        // Filtros específicos usando applyCondition (heredado de BaseService)
        applyCondition(builder, filter.name(), qEntity.name::containsIgnoreCase);
        applyCondition(builder, filter.executionPackageName(), qEntity.executionPackage.name::containsIgnoreCase);
        applyCondition(builder, filter.stationName(),
                name -> qEntity.stations.any().name.containsIgnoreCase(name));

        // Filtro por estado: solo si viene; ausente no filtra (ver TrackFilter)
        applyCondition(builder, filter.enabled(), qEntity.enabled::eq);

        // Búsqueda general (SearchText)
        Optional.ofNullable(filter.searchText())
                .filter(text -> !text.isBlank())
                .ifPresent(text -> {
                    BooleanBuilder searchBuilder = new BooleanBuilder();
                    searchBuilder.or(qEntity.name.containsIgnoreCase(text));
                    searchBuilder.or(qEntity.executionPackage.name.containsIgnoreCase(text));
                    // Nota: estación es opcional en la entidad Track
                    searchBuilder.or(qEntity.stations.any().name.containsIgnoreCase(text));

                    builder.and(searchBuilder);
                });

        return getMapper().mapToSummaryDTOs(getRepository().findAll(builder, pageable));
    }
}
