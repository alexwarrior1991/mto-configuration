package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.AnchorageFoundationType;
import com.alejandro.mtoconfiguration.mapper.lov.AnchorageFoundationTypeMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageFoundationTypeDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.AnchorageFoundationTypeRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class AnchorageFoundationTypeService
        extends AbstractLovCrudService<AnchorageFoundationTypeDTO, AnchorageFoundationType> {


    public AnchorageFoundationTypeService(
            AnchorageFoundationTypeRepository repository,
            AnchorageFoundationTypeMapper mapper,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        super(repository, mapper, applicationEventPublisher);
    }

    @Override
    protected String getEntityName() {
        return "AnchorageFoundationType";
    }
}
