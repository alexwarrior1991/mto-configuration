package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.Portal;
import com.alejandro.mtoconfiguration.entity.lov.PortalType;
import com.alejandro.mtoconfiguration.mapper.lov.PortalMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.PortalDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.PortalRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.PortalTypeRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovRelationResolver;
import org.springframework.stereotype.Service;

@Service
public class PortalService extends AbstractLovCrudService<PortalDTO, Portal> {

    private final PortalTypeRepository portalTypeRepository;
    private final LovRelationResolver lovRelationResolver;

    public PortalService(
            PortalRepository repository,
            PortalMapper mapper,
            PortalTypeRepository portalTypeRepository,
            LovRelationResolver lovRelationResolver
    ) {
        super(repository, mapper);
        this.portalTypeRepository = portalTypeRepository;
        this.lovRelationResolver = lovRelationResolver;
    }

    @Override
    protected void beforeCreate(PortalDTO dto, Portal entity) {
        resolvePortalType(dto, entity);
    }

    @Override
    protected void beforeUpdate(PortalDTO dto, Portal entity) {
        resolvePortalType(dto, entity);
    }

    private void resolvePortalType(PortalDTO dto, Portal entity) {
        PortalType portalType = lovRelationResolver.resolveRequired(
                dto.getPortalType(),
                portalTypeRepository,
                "PortalType"
        );

        entity.setPortalType(portalType);
    }

    @Override
    protected String getEntityName() {
        return "Portal";
    }

}
