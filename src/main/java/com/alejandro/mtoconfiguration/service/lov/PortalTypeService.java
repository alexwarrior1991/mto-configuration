package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.PortalType;
import com.alejandro.mtoconfiguration.mapper.lov.PortalTypeMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.PortalTypeDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.PortalTypeRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import org.springframework.stereotype.Service;

@Service
public class PortalTypeService extends AbstractLovCrudService<PortalTypeDTO, PortalType> {

    public PortalTypeService(
            PortalTypeRepository repository,
            PortalTypeMapper mapper
    ) {
        super(repository, mapper);
    }

    @Override
    protected String getEntityName() {
        return "PortalType";
    }
}
