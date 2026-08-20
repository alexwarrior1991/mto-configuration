package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.DisconnectorFunction;
import com.alejandro.mtoconfiguration.mapper.lov.DisconnectorFunctionMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.DisconnectorFunctionRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class DisconnectorFunctionService extends AbstractLovCrudService<DisconnectorFunctionDTO, DisconnectorFunction> {

    public DisconnectorFunctionService(
            DisconnectorFunctionRepository repository,
            DisconnectorFunctionMapper mapper,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        super(repository, mapper, applicationEventPublisher);
    }

    @Override
    protected String getEntityName() {
        return "DisconnectorFunction";
    }
}
