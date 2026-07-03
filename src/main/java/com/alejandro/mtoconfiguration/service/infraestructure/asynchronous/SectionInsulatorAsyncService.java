package com.alejandro.mtoconfiguration.service.infraestructure.asynchronous;

import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.service.commons.BaseAsyncService;
import com.alejandro.mtoconfiguration.service.infraestructure.SectionInsulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SectionInsulatorAsyncService extends BaseAsyncService<SectionInsulatorDTO, SectionInsulator, SectionInsulatorService> {

    private final SectionInsulatorService sectionInsulatorService;

    @Override
    protected SectionInsulatorService getService() {
        return sectionInsulatorService;
    }
}
