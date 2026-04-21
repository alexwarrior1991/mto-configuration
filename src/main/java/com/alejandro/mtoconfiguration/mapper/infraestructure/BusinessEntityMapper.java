package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.configuration.BusinessEntity;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ToEntityIgnoreAudit;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.BusinessEntityDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
        config = CentralConfigMapper.class,
        uses = {
                ReferenceMapper.class
        }
)
public abstract class BusinessEntityMapper implements BaseMapper<BusinessEntityDTO, BusinessEntity> {

    @Autowired
    protected MasterDataService masterDataService;

    @Override
    @Mapping(target = "comercialEntityType", ignore = true)
    public abstract BusinessEntityDTO toDTO(BusinessEntity entity);

    @Override
    @ToEntityIgnoreAudit
    @Mapping(target = "comercialEntityType", ignore = true) // Gestionado en AfterMapping
    public abstract BusinessEntity toEntity(BusinessEntityDTO dto);

    @Override
    @ToEntityIgnoreAudit
    @Mapping(target = "comercialEntityType", ignore = true) // Gestionado en AfterMapping
    public abstract void updateEntityFromDTO(BusinessEntityDTO dto, @MappingTarget BusinessEntity entity);

    // --- AFTER MAPPING: DTO -> ENTITY (Persistencia) ---

    @AfterMapping
    protected void mapDtoToEntity(BusinessEntityDTO dto, @MappingTarget BusinessEntity entity) {
        // Resolución del LOV: ComercialEntityType
        if (dto.getComercialEntityType() != null && dto.getComercialEntityType().getCode() != null) {
            entity.setComercialEntityType(
                    masterDataService.getComercialEntityTypeByCode(dto.getComercialEntityType().getCode())
            );
        }
    }


    // --- AFTER MAPPING: ENTITY -> DTO (Consulta) ---

    @AfterMapping
    protected void mapEntityToDto(BusinessEntity entity, @MappingTarget BusinessEntityDTO dto) {
        // Enriquecimiento del DTO usando la caché del MasterDataService
        if (entity.getComercialEntityType() != null && entity.getComercialEntityType().getId() != null) {
            dto.setComercialEntityType(
                    masterDataService.getComercialEntityTypeByIdAndMapToDTO(entity.getComercialEntityType().getId())
            );
        }
    }
}
