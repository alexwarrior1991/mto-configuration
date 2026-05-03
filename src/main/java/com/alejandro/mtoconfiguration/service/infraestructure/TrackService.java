package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.TrackBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.QTrack;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.infraestructure.TrackMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.TrackFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackRepository;
import com.alejandro.mtoconfiguration.service.commons.AsyncBaseService;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.utils.InfrastructureUtils;
import com.alejandro.mtoconfiguration.utils.PermissionUtils;
import com.alejandro.mtoconfiguration.validator.infrastructure.TrackValidator;
import com.querydsl.core.BooleanBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class TrackService extends AsyncBaseService<TrackDTO, Track>
        implements ITrackService {

    private final TrackRepository repository;
    private final TrackMapper mapper;
    private final TrackValidator validator;
    private final TrackBusiness business;
    private final TrackCriteriaSearchRepository criteriaSearchRepository;
    private final MasterDataService masterDataService;
    private final InfrastructureUtils infrastructureUtils;

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

    @Override
    public Page<TrackDTO> getTracks(Pageable pageable, TrackFilter filter) {
        log.info("Consultando vías con filtros funcionales");

        QTrack qEntity = QTrack.track;
        BooleanBuilder builder = new BooleanBuilder();

        // Filtros específicos usando applyCondition (heredado de BaseService)
        applyCondition(builder, filter.name(), qEntity.name::containsIgnoreCase);
        applyCondition(builder, filter.executionPackageName(), qEntity.executionPackage.name::containsIgnoreCase);
        applyCondition(builder, filter.stationName(), qEntity.station.name::containsIgnoreCase);

        // Filtro por estado
        builder.and(qEntity.enabled.eq(filter.enabled()));

        // Búsqueda general (SearchText)
        Optional.ofNullable(filter.searchText())
                .filter(text -> !text.isBlank())
                .ifPresent(text -> {
                    BooleanBuilder searchBuilder = new BooleanBuilder();
                    searchBuilder.or(qEntity.name.containsIgnoreCase(text));
                    searchBuilder.or(qEntity.executionPackage.name.containsIgnoreCase(text));
                    // Nota: estación es opcional en la entidad Track
                    searchBuilder.or(qEntity.station.name.containsIgnoreCase(text));

                    builder.and(searchBuilder);
                });

        return getRepository().findAll(builder, pageable)
                .map(getMapper()::toDTO);
    }
}
