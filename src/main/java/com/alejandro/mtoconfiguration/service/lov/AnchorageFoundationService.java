package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.AnchorageFoundation;
import com.alejandro.mtoconfiguration.entity.lov.AnchorageFoundationType;
import com.alejandro.mtoconfiguration.mapper.lov.AnchorageFoundationMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageFoundationDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.AnchorageFoundationRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.AnchorageFoundationTypeRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovRelationResolver;
import org.springframework.stereotype.Service;

@Service
public class AnchorageFoundationService extends AbstractLovCrudService<AnchorageFoundationDTO, AnchorageFoundation> {

    private final AnchorageFoundationTypeRepository anchorageFoundationTypeRepository;
    private final LovRelationResolver lovRelationResolver;

    public AnchorageFoundationService(
            AnchorageFoundationRepository repository,
            AnchorageFoundationMapper mapper,
            AnchorageFoundationTypeRepository anchorageFoundationTypeRepository,
            LovRelationResolver lovRelationResolver
    ) {
        super(repository, mapper);
        this.anchorageFoundationTypeRepository = anchorageFoundationTypeRepository;
        this.lovRelationResolver = lovRelationResolver;
    }

    @Override
    protected void beforeCreate(AnchorageFoundationDTO dto, AnchorageFoundation entity) {
        resolveAnchorageFoundationType(dto, entity);
    }

    @Override
    protected void beforeUpdate(AnchorageFoundationDTO dto, AnchorageFoundation entity) {
        resolveAnchorageFoundationType(dto, entity);
    }

    private void resolveAnchorageFoundationType(AnchorageFoundationDTO dto, AnchorageFoundation entity) {
        AnchorageFoundationType anchorageFoundationType = lovRelationResolver.resolveRequired(
                dto.getAnchorageFoundationType(),
                anchorageFoundationTypeRepository,
                "AnchorageFoundationType"
        );

        entity.setAnchorageFoundationType(anchorageFoundationType);
    }

    @Override
    protected String getEntityName() {
        return "AnchorageFoundation";
    }

}
