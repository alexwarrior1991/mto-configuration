package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.CantileverType;
import com.alejandro.mtoconfiguration.mapper.lov.CantileverTypeMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.CantileverTypeDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.CantileverTypeRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import org.springframework.stereotype.Service;

@Service
public class CantileverTypeService extends AbstractLovCrudService<CantileverTypeDTO, CantileverType> {

    public CantileverTypeService(
            CantileverTypeRepository repository,
            CantileverTypeMapper mapper
    ) {
        super(repository, mapper);
    }

    @Override
    protected String getEntityName() {
        return "CantileverType";
    }
}
