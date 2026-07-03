package com.alejandro.mtoconfiguration.service.infraestructure.asynchronous;

import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.service.commons.BaseAsyncService;
import com.alejandro.mtoconfiguration.service.infraestructure.DisconnectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DisconnectorAsyncService extends BaseAsyncService<DisconnectorDTO, Disconnector, DisconnectorService> {

    private final DisconnectorService disconnectorService;

    @Override
    protected DisconnectorService getService() {
        return disconnectorService;
    }
}
