package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.SteadyArmType;
import com.alejandro.mtoconfiguration.mapper.lov.SteadyArmTypeMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SteadyArmTypeDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.SteadyArmTypeRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class SteadyArmTypeService extends AbstractLovCrudService<SteadyArmTypeDTO, SteadyArmType> {

    public SteadyArmTypeService(
            SteadyArmTypeRepository repository,
            SteadyArmTypeMapper mapper,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        super(repository, mapper, applicationEventPublisher);
    }

    @Override
    protected String getEntityName() {
        return "SteadyArmType";
    }
}
