package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.SectionInsulatorBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.QSectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
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
import com.querydsl.core.BooleanBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;

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

    @Override
    public Page<SectionInsulatorDTO> getSectionInsulators(Pageable pageable, SectionInsulatorFilter filter) {
        log.info("Consultando aisladores de sección con filtros funcionales");

        QSectionInsulator qEntity = QSectionInsulator.sectionInsulator;
        BooleanBuilder builder = new BooleanBuilder();

        applyCondition(builder, filter.name(), qEntity.name::containsIgnoreCase);
        applyCondition(builder, filter.stationName(), qEntity.station.name::containsIgnoreCase);
        applyCondition(builder, filter.trackName(), qEntity.track.name::containsIgnoreCase);

        // any() sobre la colección genera un EXISTS, no un JOIN: la página no se duplica cuando un
        // aislador tiene varias agujas.
        applyCondition(builder, filter.switchCode(),
                code -> qEntity.switches.any().code.containsIgnoreCase(code));

        Optional.ofNullable(filter.installationType())
                .ifPresent(type -> builder.and(qEntity.installationType.eq(type)));

        builder.and(qEntity.enabled.eq(filter.enabled()));

        // Búsqueda general (SearchText)
        Optional.ofNullable(filter.searchText())
                .filter(text -> !text.isBlank())
                .ifPresent(text -> {
                    BooleanBuilder searchBuilder = new BooleanBuilder();
                    searchBuilder.or(qEntity.name.containsIgnoreCase(text));
                    searchBuilder.or(qEntity.station.name.containsIgnoreCase(text));
                    searchBuilder.or(qEntity.track.name.containsIgnoreCase(text));
                    searchBuilder.or(qEntity.switches.any().code.containsIgnoreCase(text));
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
