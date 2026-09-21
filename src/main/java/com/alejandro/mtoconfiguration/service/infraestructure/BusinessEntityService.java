package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.business.commons.Business;
import com.alejandro.mtoconfiguration.entity.configuration.BusinessEntity;
import com.alejandro.mtoconfiguration.mapper.infraestructure.BusinessEntityMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.BusinessEntityDTO;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.BusinessEntityRepository;
import com.alejandro.mtoconfiguration.service.commons.BaseService;
import com.alejandro.mtoconfiguration.validator.commons.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Entidades de negocio —las empresas a las que apunta {@code companyId} de un paquete de
 * ejecucion—, solo lectura.
 *
 * <p>Se dan de alta con el maestro de perfiles ({@code topology.yml}, por NIF), no por la API. Lo
 * que la API necesita es poder elegir una al crear o modificar un paquete, y para eso basta con
 * listarlas y leerlas por id. Por eso extiende {@link BaseService} y no {@code CRUDService}: sin
 * validador, sin negocio y sin busqueda por criterios.</p>
 */
@Service
@RequiredArgsConstructor
public class BusinessEntityService extends BaseService<BusinessEntityDTO, BusinessEntity> {

    private final BusinessEntityRepository repository;
    private final BusinessEntityMapper mapper;

    @Override
    protected BusinessEntityMapper getMapper() {
        return mapper;
    }

    @Override
    protected Validator<BusinessEntityDTO> getValidator() {
        return null;
    }

    @Override
    public BusinessEntity getEntity() {
        return new BusinessEntity();
    }

    @Override
    public BusinessEntityDTO getDTO() {
        return new BusinessEntityDTO();
    }

    @Override
    protected BusinessEntityRepository getRepository() {
        return repository;
    }

    @Override
    protected CriteriaSearchRepository<BusinessEntity> getCriteriaSearchRepository() {
        return null;
    }

    @Override
    protected Map<String, Object> searchParams() {
        return Map.of();
    }

    @Override
    protected Business<BusinessEntityDTO, BusinessEntity> getBusiness() {
        return null;
    }
}
