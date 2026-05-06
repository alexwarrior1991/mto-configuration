package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.ProfileBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.QProfile;
import com.alejandro.mtoconfiguration.mapper.infraestructure.ProfileMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ProfileFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.service.commons.AsyncBaseService;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.utils.InfrastructureUtils;
import com.alejandro.mtoconfiguration.utils.PermissionUtils;
import com.alejandro.mtoconfiguration.utils.WindowIterator;
import com.alejandro.mtoconfiguration.validator.infrastructure.ProfileValidator;
import com.querydsl.core.BooleanBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ProfileService extends AsyncBaseService<ProfileDTO, Profile>
        implements IProfileService {

    private final ProfileRepository repository;
    private final ProfileMapper mapper;
    private final ProfileValidator validator;
    private final ProfileBusiness business;
    private final ProfileCriteriaSearchRepository criteriaSearchRepository;
    private final MasterDataService masterDataService;
    private final InfrastructureUtils infrastructureUtils;

    @PersistenceContext
    private EntityManager entityManager;

    // --- Métodos de configuración de Base Service ---

    @Override
    protected ProfileMapper getMapper() {
        return mapper;
    }

    @Override
    protected ProfileValidator getValidator() {
        return validator;
    }

    @Override
    public Profile getEntity() {
        return new Profile();
    }

    @Override
    public ProfileDTO getDTO() {
        return new ProfileDTO();
    }

    @Override
    protected ProfileRepository getRepository() {
        return repository;
    }

    @Override
    protected ProfileCriteriaSearchRepository getCriteriaSearchRepository() {
        return criteriaSearchRepository;
    }

    @Override
    protected ProfileBusiness getBusiness() {
        return business;
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
    public Page<ProfileDTO> getProfiles(Pageable pageable, ProfileFilter filter) {
        log.info("Buscando perfiles con filtros por código LOV: {}", filter);

        QProfile qEntity = QProfile.profile;
        BooleanBuilder builder = new BooleanBuilder();

        // Filtros directos y de texto
        applyCondition(builder, filter.profileId(), qEntity.profileId::containsIgnoreCase);
        applyCondition(builder, filter.kp(), qEntity.kp::eq);

        // Filtros de navegación (Track y Station)
        applyCondition(builder, filter.trackId(), qEntity.track.id::eq);
        applyCondition(builder, filter.trackName(), qEntity.track.name::containsIgnoreCase);
        applyCondition(builder, filter.stationName(), qEntity.track.station.name::containsIgnoreCase);

        // Filtros por CÓDIGO de las LOVs
        applyCondition(builder, filter.anchorageCode(), qEntity.anchorage.code::eq);
        applyCondition(builder, filter.anchorageFoundationCode(), qEntity.anchorageFoundation.code::eq);
        applyCondition(builder, filter.foundationCode(), qEntity.foundation.code::eq);
        applyCondition(builder, filter.poleTypeCode(), qEntity.poleType.code::eq);
        applyCondition(builder, filter.portalCode(), qEntity.portal.code::eq);
        applyCondition(builder, filter.profileStatusCode(), qEntity.profileStatus.code::eq);
        applyCondition(builder, filter.returnSupportCode(), qEntity.returnSupport.code::eq);
        applyCondition(builder, filter.sectioningCode(), qEntity.sectioning.code::eq);

        // Búsqueda general (SearchText)
        Optional.ofNullable(filter.searchText())
                .filter(text -> !text.isBlank())
                .ifPresent(text -> {
                    BooleanBuilder searchBuilder = new BooleanBuilder();
                    searchBuilder.or(qEntity.profileId.containsIgnoreCase(text));
                    searchBuilder.or(qEntity.track.name.containsIgnoreCase(text));
                    searchBuilder.or(qEntity.track.station.name.containsIgnoreCase(text));
                    builder.and(searchBuilder);
                });

        return getRepository().findAll(builder, pageable)
                .map(getMapper()::toDTO);
    }

    @Override
    public void processProfilesByTrack(Long trackId, Consumer<Profile> processor) {
        int pageSize = 500; // Tamaño óptimo de ventana
        Pageable pageRequest = PageRequest.of(0, pageSize);

        // Definimos la lógica de "búsqueda de la siguiente ventana"
        Function<Profile, List<Profile>> fetchNextWindow = (lastProfile) -> {
            if (lastProfile == null) {
                return repository.findByTrackIdOrderByKpAscIdAsc(trackId, pageRequest);
            }
            return repository.findNextPage(trackId, lastProfile.getKp(), lastProfile.getId(), pageRequest);
        };

        // Creamos el iterador con nuestra lambda
        WindowIterator<Profile> iterator = new WindowIterator<>(fetchNextWindow);

        // Procesamos cada ventana de forma eficiente
        iterator.forEachRemaining(window -> {
            log.info("Procesando ventana de {} perfiles para la vía {}", window.size(), trackId);
            window.forEach(profile -> {
                processor.accept(profile);
                // PASO CLAVE: Desacoplar el objeto individual para liberar memoria
                entityManager.detach(profile);
            });
        });
    }

    @Override
    public List<ProfileDTO> getProfilesByKeyset(Long trackId, BigDecimal lastKp, Long lastId, int pageSize) {
        log.debug("Petición de ventana de perfiles para vía {} desde KP {}", trackId, lastKp);

        Pageable pageRequest = PageRequest.of(0, pageSize);
        List<Profile> entityPage;

        if (lastKp == null || lastId == null) {
            // Primera carga: traemos desde el principio de la vía
            entityPage = repository.findByTrackIdOrderByKpAscIdAsc(trackId, pageRequest);
        } else {
            // Carga de "Siguiente": usamos el Keyset para saltar instantáneamente al punto
            entityPage = repository.findNextPage(trackId, lastKp, lastId, pageRequest);
        }

        // Convertimos a DTO para el frontend
        return entityPage.stream()
                .map(mapper::toDTO)
                .toList();
    }

    @Override
    public List<ProfileDTO> getProfilesByKpRange(Long trackId, BigDecimal startKp, BigDecimal endKp) {
        log.debug("Obteniendo sector de vía {}: del KP {} al {}", trackId, startKp, endKp);

        // Llamada al repositorio (aprovecha el índice compuesto)
        List<Profile> profiles = repository.findByKpRange(trackId, startKp, endKp);

        // Transformación funcional a DTO
        return profiles.stream()
                .map(mapper::toDTO)
                .toList();
    }
}
