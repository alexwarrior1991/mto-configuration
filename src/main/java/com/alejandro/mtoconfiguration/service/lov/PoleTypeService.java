package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.PoleType;
import com.alejandro.mtoconfiguration.mapper.lov.PoleTypeMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.PoleTypeDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.PoleTypeRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class PoleTypeService extends AbstractLovCrudService<PoleTypeDTO, PoleType> {

    public PoleTypeService(
            PoleTypeRepository repository,
            PoleTypeMapper mapper,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        super(repository, mapper, applicationEventPublisher);
    }

    @Override
    protected String getEntityName() {
        return "PoleType";
    }
}