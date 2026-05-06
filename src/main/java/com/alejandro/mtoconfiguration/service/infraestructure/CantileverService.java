package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.infrastructure.CantileverBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.QCantilever;
import com.alejandro.mtoconfiguration.mapper.infraestructure.CantileverMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.CantileverFilter;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.CantileverCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.CantileverRepository;
import com.alejandro.mtoconfiguration.service.commons.AsyncBaseService;
import com.alejandro.mtoconfiguration.utils.InfrastructureUtils;
import com.alejandro.mtoconfiguration.utils.PermissionUtils;
import com.alejandro.mtoconfiguration.validator.infrastructure.CantileverValidator;
import com.querydsl.core.BooleanBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CantileverService extends AsyncBaseService<CantileverDTO, Cantilever> implements ICantileverService {

    private final CantileverRepository repository;
    private final CantileverMapper mapper;
    private final CantileverValidator validator;
    private final CantileverBusiness business;
    private final CantileverCriteriaSearchRepository criteriaSearchRepository;
    private final InfrastructureUtils infrastructureUtils;

    @Override
    protected CantileverMapper getMapper() {
        return mapper;
    }

    @Override
    protected CantileverValidator getValidator() {
        return validator;
    }

    @Override
    public Cantilever getEntity() {
        return new Cantilever();
    }

    @Override
    public CantileverDTO getDTO() {
        return new CantileverDTO();
    }

    @Override
    protected CantileverRepository getRepository() {
        return repository;
    }

    @Override
    protected CantileverCriteriaSearchRepository getCriteriaSearchRepository() {
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
    protected CantileverBusiness getBusiness() {
        return business;
    }

    @Override
    public Page<CantileverDTO> getCantilevers(Pageable pageable, CantileverFilter filter) {
        QCantilever qCantilever = QCantilever.cantilever;
        BooleanBuilder builder = new BooleanBuilder();

        applyCondition(builder, filter.profileId(), qCantilever.profile.id::eq);
        applyCondition(builder, filter.profileCode(), qCantilever.profile.profileId::eq);
        applyCondition(builder, filter.cantileverTypeCode(), qCantilever.cantileverType.code::eq);

        // Filtros por rangos técnicos
        applyCondition(builder, filter.cwHeightMin(), qCantilever.cwHeight::goe);
        applyCondition(builder, filter.cwHeightMax(), qCantilever.cwHeight::loe);

        applyCondition(builder, filter.staggerMin(), qCantilever.stagger::goe);
        applyCondition(builder, filter.staggerMax(), qCantilever.stagger::loe);

        // Búsqueda general
        Optional.ofNullable(filter.searchText())
                .filter(text -> !text.isBlank())
                .ifPresent(text -> {
                    BooleanBuilder searchBuilder = new BooleanBuilder();
                    searchBuilder.or(qCantilever.cantileverType.code.containsIgnoreCase(text));
                    searchBuilder.or(qCantilever.profile.profileId.containsIgnoreCase(text));
                    builder.and(searchBuilder);
                });

        return repository.findAll(builder, pageable).map(mapper::toDTO);
    }

    @Override
    public List<CantileverDTO> getByProfileId(Long profileId) {
        return repository.findByProfileId(profileId).stream()
                .map(mapper::toDTO)
                .toList();
    }

}
