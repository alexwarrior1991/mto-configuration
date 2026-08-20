package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.Sectioning;
import com.alejandro.mtoconfiguration.mapper.lov.SectioningMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SectioningDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.SectioningRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class SectioningService extends AbstractLovCrudService<SectioningDTO, Sectioning> {

    public SectioningService(
            SectioningRepository repository,
            SectioningMapper mapper,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        super(repository, mapper, applicationEventPublisher);
    }

    @Override
    protected String getEntityName() {
        return "Sectioning";
    }
}
