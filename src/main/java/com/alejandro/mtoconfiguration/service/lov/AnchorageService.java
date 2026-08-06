package com.alejandro.mtoconfiguration.service.lov;

import com.alejandro.mtoconfiguration.entity.lov.Anchorage;
import com.alejandro.mtoconfiguration.mapper.lov.AnchorageMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.AnchorageRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import com.alejandro.mtoconfiguration.service.lov.commons.AbstractLovCrudService;
import org.springframework.stereotype.Service;

@Service
public class AnchorageService extends AbstractLovCrudService<AnchorageDTO, Anchorage> {

    public AnchorageService(AnchorageRepository repository, AnchorageMapper mapper) {
        super(repository, mapper);
    }

    @Override
    protected String getEntityName() {
        return "Anchorage";
    }
}
