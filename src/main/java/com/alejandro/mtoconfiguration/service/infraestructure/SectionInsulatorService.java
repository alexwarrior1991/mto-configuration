package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.SectionInsulatorBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.QSectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.mapper.infraestructure.SectionInsulatorMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.SectionInsulatorFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
import com.alejandro.mtoconfiguration.service.commons.AsyncBaseService;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.utils.InfrastructureUtils;
import com.alejandro.mtoconfiguration.utils.PermissionUtils;
import com.alejandro.mtoconfiguration.validator.infrastructure.SectionInsulatorValidator;
import com.querydsl.core.BooleanBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SectionInsulatorService extends AsyncBaseService<SectionInsulatorDTO, SectionInsulator>
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

    @Override
    public Page<SectionInsulatorDTO> getSectionInsulators(Pageable pageable, SectionInsulatorFilter filter) {
        log.info("Consultando aisladores de sección con filtros funcionales");

        QSectionInsulator qEntity = QSectionInsulator.sectionInsulator;
        BooleanBuilder builder = new BooleanBuilder();

        applyCondition(builder, filter.name(), qEntity.name::containsIgnoreCase);
        applyCondition(builder, filter.stationName(), qEntity.station.name::containsIgnoreCase);

        builder.and(qEntity.enabled.eq(filter.enabled()));

        // Búsqueda general (SearchText)
        Optional.ofNullable(filter.searchText())
                .filter(text -> !text.isBlank())
                .ifPresent(text -> {
                    BooleanBuilder searchBuilder = new BooleanBuilder();
                    searchBuilder.or(qEntity.name.containsIgnoreCase(text));
                    searchBuilder.or(qEntity.station.name.containsIgnoreCase(text));
                    builder.and(searchBuilder);
                });

        return getRepository().findAll(builder, pageable)
                .map(getMapper()::toDTO);
    }

    @Override
    public List<SectionInsulatorDTO> getSectionInsulatorsByStationId(Long stationId) {
        return Optional.ofNullable(stationId)
                .map(repository::findByStationId)
                .map(list -> list.stream().map(getMapper()::toDTO).toList())
                .orElseGet(List::of);
    }

    @Override
    public List<SectionInsulatorDTO> getSectionInsulatorsByStationName(String stationName) {
        return Optional.ofNullable(stationName)
                .filter(name -> !name.isBlank())
                .map(repository::findByStationNameContainingIgnoreCase)
                .map(list -> list.stream().map(getMapper()::toDTO).toList())
                .orElseGet(List::of);
    }
}
