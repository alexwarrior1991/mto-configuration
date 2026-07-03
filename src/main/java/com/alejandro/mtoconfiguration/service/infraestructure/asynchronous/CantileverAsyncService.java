package com.alejandro.mtoconfiguration.service.infraestructure.asynchronous;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.service.commons.BaseAsyncService;
import com.alejandro.mtoconfiguration.service.infraestructure.CantileverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CantileverAsyncService extends BaseAsyncService<CantileverDTO, Cantilever, CantileverService> {

    private final CantileverService cantileverService;

    @Override
    protected CantileverService getService() {
        return cantileverService;
    }

}
