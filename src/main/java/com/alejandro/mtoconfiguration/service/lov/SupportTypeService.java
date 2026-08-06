package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.SupportType;
import com.alejandro.mtoconfiguration.mapper.lov.SupportTypeMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SupportTypeDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.SupportTypeRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import org.springframework.stereotype.Service;

@Service
public class SupportTypeService extends AbstractLovCrudService<SupportTypeDTO, SupportType> {

    public SupportTypeService(
            SupportTypeRepository repository,
            SupportTypeMapper mapper
    ) {
        super(repository, mapper);
    }

    @Override
    protected String getEntityName() {
        return "SupportType";
    }
}
