package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.CantileverFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ICantileverService {

    Page<CantileverDTO> getCantilevers(Pageable pageable, CantileverFilter filter);

    List<CantileverDTO> getByProfileId(Long profileId);
}
