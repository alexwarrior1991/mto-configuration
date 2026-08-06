package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.ComercialEntityType;
import com.alejandro.mtoconfiguration.mapper.lov.ComercialEntityTypeMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ComercialEntityTypeDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.ComercialEntityTypeRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import org.springframework.stereotype.Service;

@Service
public class ComercialEntityTypeService extends AbstractLovCrudService<ComercialEntityTypeDTO, ComercialEntityType> {

    public ComercialEntityTypeService(
            ComercialEntityTypeRepository repository,
            ComercialEntityTypeMapper mapper
    ) {
        super(repository, mapper);
    }

    @Override
    protected String getEntityName() {
        return "ComercialEntityType";
    }
}
