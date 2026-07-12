package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.SteadyArmBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.QSteadyArm;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.mapper.infraestructure.SteadyArmMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.SteadyArmFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SteadyArmCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SteadyArmRepository;
import com.alejandro.mtoconfiguration.service.commons.CRUDService;
import com.alejandro.mtoconfiguration.utils.InfrastructureUtils;
import com.alejandro.mtoconfiguration.utils.PermissionUtils;
import com.alejandro.mtoconfiguration.validator.infrastructure.SteadyArmValidator;
import com.querydsl.core.BooleanBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SteadyArmService extends CRUDService<SteadyArmDTO, SteadyArm> implements ISteadyArmService {

    private final SteadyArmRepository repository;
    private final SteadyArmMapper mapper;
    private final SteadyArmValidator validator;
    private final SteadyArmBusiness business;
    private final SteadyArmCriteriaSearchRepository criteriaSearchRepository;
    private final InfrastructureUtils infrastructureUtils;

    @Override
    protected SteadyArmMapper getMapper() {
        return mapper;
    }

    @Override
    protected SteadyArmValidator getValidator() {
        return validator;
    }

    @Override
    public SteadyArm getEntity() {
        return new SteadyArm();
    }

    @Override
    public SteadyArmDTO getDTO() {
        return new SteadyArmDTO();
    }

    @Override
    protected SteadyArmRepository getRepository() {
        return repository;
    }

    @Override
    protected SteadyArmCriteriaSearchRepository getCriteriaSearchRepository() {
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
    protected SteadyArmBusiness getBusiness() {
        return business;
    }

    @Override
    public Page<SteadyArmDTO> getSteadyArms(Pageable pageable, SteadyArmFilter filter) {
        QSteadyArm qSteadyArm = QSteadyArm.steadyArm;
        BooleanBuilder builder = new BooleanBuilder();

        applyCondition(builder, filter.cantileverId(), qSteadyArm.cantilever.id::eq);
        applyCondition(builder, filter.steadyArmTypeCode(), qSteadyArm.steadyArmType.code::eq);

        applyCondition(builder, filter.lengthMin(), qSteadyArm.length::goe);
        applyCondition(builder, filter.lengthMax(), qSteadyArm.length::loe);

        // Búsqueda general
        Optional.ofNullable(filter.searchText())
                .filter(text -> !text.isBlank())
                .ifPresent(text -> {
                    BooleanBuilder searchBuilder = new BooleanBuilder();
                    searchBuilder.or(qSteadyArm.steadyArmType.code.containsIgnoreCase(text));
                    // Podríamos añadir más campos si fuera necesario
                    builder.and(searchBuilder);
                });

        return repository.findAll(builder, pageable).map(mapper::toDTO);
    }

    @Override
    public Optional<SteadyArmDTO> getByCantileverId(Long cantileverId) {
        return repository.findByCantileverId(cantileverId).map(mapper::toDTO);
    }
}
