package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.DisconnectorBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.QDisconnector;
import com.alejandro.mtoconfiguration.mapper.infraestructure.DisconnectorMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.DisconnectorFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.service.commons.CRUDService;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.utils.InfrastructureUtils;
import com.alejandro.mtoconfiguration.validator.infrastructure.DisconnectorValidator;
import com.querydsl.core.BooleanBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @Override
    public Page<DisconnectorDTO> getDisconnectors(Pageable pageable, DisconnectorFilter filter) {
        log.info("Consultando seccionadores con filtros funcionales");
        QDisconnector qEntity = QDisconnector.disconnector;
        BooleanBuilder builder = new BooleanBuilder();

        applyCondition(builder, filter.name(), qEntity.name::containsIgnoreCase);
        applyCondition(builder, filter.stationName(), qEntity.station.name::containsIgnoreCase);
        applyCondition(builder, filter.functionName(), qEntity.disconnectorFunction.description::containsIgnoreCase);

        // Filtro por la propiedad específica onLoad
        builder.and(qEntity.onLoad.eq(filter.onLoad()));

        // Búsqueda general (SearchText)
        Optional.ofNullable(filter.searchText())
                .filter(text -> !text.isBlank())
                .ifPresent(text -> {
                    BooleanBuilder searchBuilder = new BooleanBuilder();
                    searchBuilder.or(qEntity.name.containsIgnoreCase(text));
                    searchBuilder.or(qEntity.station.name.containsIgnoreCase(text));
                    searchBuilder.or(qEntity.disconnectorFunction.description.containsIgnoreCase(text));

                    builder.and(searchBuilder);
                });

        return getRepository().findAll(builder, pageable)
                .map(getMapper()::toDTO);
    }

    @Override
    public List<DisconnectorDTO> getDisconnectorByStationId(Long stationId) {
        return Optional.ofNullable(stationId)
                .map(repository::findByStationId)
                .map(list -> list.stream().map(mapper::toDTO).toList())
                .orElseGet(List::of);
    }

    @Override
    public List<DisconnectorDTO> getDisconnectorsByStationName(String stationName) {
        return Optional.ofNullable(stationName)
                .filter(name -> !name.isBlank())
                .map(repository::findByStationNameContainingIgnoreCase)
                .map(list -> list.stream().map(mapper::toDTO).toList())
                .orElseGet(List::of);
    }

    /**
     * DisconnectorDTO no embebe otros DTO de entidad, solo LOV, que se editan casi nunca.
     * Sus entradas no se quedan obsoletas al cambiar otra entidad, asi que si
     * compensa cachearlas. Ver BaseService.isCacheable().
     */
    @Override
    public boolean isCacheable() {
        return true;
    }
}
