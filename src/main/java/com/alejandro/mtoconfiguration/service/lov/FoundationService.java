package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.Foundation;
import com.alejandro.mtoconfiguration.entity.lov.FoundationType;
import com.alejandro.mtoconfiguration.mapper.lov.FoundationMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.FoundationDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.FoundationRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.FoundationTypeRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovRelationResolver;
import org.springframework.stereotype.Service;

@Service
public class FoundationService extends AbstractLovCrudService<FoundationDTO, Foundation> {

    private final FoundationTypeRepository foundationTypeRepository;
    private final LovRelationResolver lovRelationResolver;


    public FoundationService(
            FoundationRepository repository,
            FoundationMapper mapper,
            FoundationTypeRepository foundationTypeRepository,
            LovRelationResolver lovRelationResolver
    ) {
        super(repository, mapper);
        this.foundationTypeRepository = foundationTypeRepository;
        this.lovRelationResolver = lovRelationResolver;
    }

    @Override
    protected void beforeCreate(FoundationDTO dto, Foundation entity) {
        resolveFoundationType(dto, entity);
    }

    @Override
    protected void beforeUpdate(FoundationDTO dto, Foundation entity) {
        resolveFoundationType(dto, entity);
    }

    private void resolveFoundationType(FoundationDTO dto, Foundation entity){
        FoundationType foundationType = lovRelationResolver.resolveRequired(
                dto.getFoundationType(),
                foundationTypeRepository,
                "FoundationType"
        );

        entity.setFoundationType(foundationType);
    }

    @Override
    protected String getEntityName() {
        return "Foundation";
    }

}
