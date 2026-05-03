package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.StationBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.QStation;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.mapper.infraestructure.StationMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.StationFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.StationCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.StationRepository;
import com.alejandro.mtoconfiguration.service.commons.AsyncBaseService;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.utils.InfrastructureUtils;
import com.alejandro.mtoconfiguration.utils.PermissionUtils;
import com.alejandro.mtoconfiguration.validator.infrastructure.StationValidator;
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
public class StationService extends AsyncBaseService<StationDTO, Station>
        implements IStationService {

    private final StationRepository repository;
    private final StationMapper mapper;
    private final StationValidator validator;
    private final StationBusiness business;
    private final StationCriteriaSearchRepository criteriaSearchRepository;
    private final MasterDataService masterDataService;
    private final InfrastructureUtils infrastructureUtils;

    @Override
    protected StationMapper getMapper() {
        return mapper;
    }

    @Override
    protected StationValidator getValidator() {
        return validator;
    }

    @Override
    public Station getEntity() {
        return new Station();
    }

    @Override
    public StationDTO getDTO() {
        return new StationDTO();
    }

    @Override
    protected StationRepository getRepository() {
        return repository;
    }

    @Override
    protected StationCriteriaSearchRepository getCriteriaSearchRepository() {
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
    protected StationBusiness getBusiness() {
        return business;
    }

    @Override
    public Page<StationDTO> getStations(Pageable pageable, StationFilter filter) {
        log.info("Checking stations");

        QStation qEntity = QStation.station;
        BooleanBuilder builder = new BooleanBuilder();

        applyCondition(builder, filter.name(), qEntity.name::containsIgnoreCase);
        applyCondition(builder, filter.executionPackageName(), qEntity.executionPackage.name::containsIgnoreCase);

        // Filtro por nombre de Track (usando .any() para colecciones)
        applyCondition(builder, filter.trackName(),
                name -> qEntity.tracks.any().name.containsIgnoreCase(name));

        // Búsqueda general (SearchText) extendida a tracks
        Optional.ofNullable(filter.searchText())
                .filter(text -> !text.isBlank())
                .ifPresent(text -> {
                    BooleanBuilder searchBuilder = new BooleanBuilder();
                    searchBuilder.or(qEntity.name.containsIgnoreCase(text));
                    searchBuilder.or(qEntity.executionPackage.name.containsIgnoreCase(text));
                    searchBuilder.or(qEntity.tracks.any().name.containsIgnoreCase(text));

                    builder.and(searchBuilder);
                });

        return getRepository().findAll(builder, pageable)
                .map(getMapper()::toDTO);
    }
}
