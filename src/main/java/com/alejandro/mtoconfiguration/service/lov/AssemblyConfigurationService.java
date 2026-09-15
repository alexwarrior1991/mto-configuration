package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.AssemblyConfiguration;
import com.alejandro.mtoconfiguration.mapper.lov.AssemblyConfigurationMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AssemblyConfigurationDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.AssemblyConfigurationRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class AssemblyConfigurationService extends AbstractLovCrudService<AssemblyConfigurationDTO, AssemblyConfiguration> {

    public AssemblyConfigurationService(
            AssemblyConfigurationRepository repository,
            AssemblyConfigurationMapper mapper,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        super(repository, mapper, applicationEventPublisher);
    }

    @Override
    protected String getEntityName() {
        return "AssemblyConfiguration";
    }
}
