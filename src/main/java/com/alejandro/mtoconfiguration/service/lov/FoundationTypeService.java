package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.FoundationType;
import com.alejandro.mtoconfiguration.mapper.lov.FoundationTypeMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.FoundationTypeDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.FoundationTypeRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import org.springframework.stereotype.Service;

@Service
public class FoundationTypeService extends AbstractLovCrudService<FoundationTypeDTO, FoundationType> {

    public FoundationTypeService(
            FoundationTypeRepository repository,
            FoundationTypeMapper mapper
    ) {
        super(repository, mapper);
    }

    @Override
    protected String getEntityName() {
        return "FoundationType";
    }
}
