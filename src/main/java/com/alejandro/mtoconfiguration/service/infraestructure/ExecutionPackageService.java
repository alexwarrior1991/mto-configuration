package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.ExecutionPackageBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.QExecutionPackage;
import com.alejandro.mtoconfiguration.mapper.infraestructure.ExecutionPackageMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ExecutionPackageFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ExecutionPackageCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ExecutionPackageRepository;
import com.alejandro.mtoconfiguration.service.commons.CRUDService;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.utils.InfrastructureUtils;
import com.alejandro.mtoconfiguration.utils.PermissionUtils;
import com.alejandro.mtoconfiguration.validator.infrastructure.ExecutionPackageValidator;
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
public class ExecutionPackageService extends CRUDService<ExecutionPackageDTO, ExecutionPackage>
        implements IExecutionPackageService {

    private final ExecutionPackageRepository repository;
    private final ExecutionPackageMapper mapper;
    private final ExecutionPackageValidator validator;
    private final ExecutionPackageBusiness business;
    private final ExecutionPackageCriteriaSearchRepository criteriaSearchRepository;
    private final MasterDataService masterDataService;
    private final InfrastructureUtils infrastructureUtils;

    @Override
    protected ExecutionPackageMapper getMapper() {
        return mapper;
    }

    @Override
    protected ExecutionPackageValidator getValidator() {
        return validator;
    }

    @Override
    public ExecutionPackage getEntity() {
        return new ExecutionPackage();
    }

    @Override
    public ExecutionPackageDTO getDTO() {
        return new ExecutionPackageDTO();
    }

    @Override
    protected ExecutionPackageRepository getRepository() {
        return repository;
    }

    @Override
    protected ExecutionPackageCriteriaSearchRepository getCriteriaSearchRepository() {
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
    protected ExecutionPackageBusiness getBusiness() {
        return business;
    }

    @Override
    public Page<ExecutionPackageDTO> getExecutionPackages(Pageable pageable, ExecutionPackageFilter filter) {

        log.info("Checking Execution Packages");

        QExecutionPackage qEntity = QExecutionPackage.executionPackage;
        BooleanBuilder builder = new BooleanBuilder();

        applyCondition(builder, filter.name(), qEntity.name::containsIgnoreCase);
        applyCondition(builder, filter.companyName(), qEntity.company.name::containsIgnoreCase);
        applyCondition(builder, filter.startDate(), qEntity.startDate::goe);
        applyCondition(builder, filter.endDate(), qEntity.endDate::loe);

        builder.and(qEntity.enabled.eq(filter.enabled()));

        Optional.ofNullable(filter.searchText())
                .filter(text -> !text.isBlank())
                .ifPresent(text -> {
                    BooleanBuilder searchBuilder = new BooleanBuilder();
                    searchBuilder.or(qEntity.name.containsIgnoreCase(text));
                    searchBuilder.or(qEntity.company.name.containsIgnoreCase(text));
                    builder.and(searchBuilder);
                });

        return getRepository().findAll(builder, pageable)
                .map(getMapper()::toDTO);
    }
}
