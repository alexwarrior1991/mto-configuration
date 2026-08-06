package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.ReturnSupport;
import com.alejandro.mtoconfiguration.mapper.lov.ReturnSupportMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ReturnSupportDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.ReturnSupportRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import org.springframework.stereotype.Service;


@Service
public class ReturnSupportService extends AbstractLovCrudService<ReturnSupportDTO, ReturnSupport> {

    public ReturnSupportService(
            ReturnSupportRepository repository,
            ReturnSupportMapper mapper
    ) {
        super(repository, mapper);
    }

    @Override
    protected String getEntityName() {
        return "ReturnSupport";
    }
}