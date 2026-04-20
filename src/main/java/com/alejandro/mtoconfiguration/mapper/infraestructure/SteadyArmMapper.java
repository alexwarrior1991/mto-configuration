package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
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
public abstract class SteadyArmMapper implements BaseMapper<SteadyArmDTO, SteadyArm> {

    @Autowired
    protected MasterDataService masterDataService;

    @Override
    @Mapping(target = "cantileverId", source = "cantilever.id")
    @Mapping(target = "steadyArmType", ignore = true) // Gestionado en AfterMapping
    public abstract SteadyArmDTO toDTO(SteadyArm entity);

    @Override
    @Mapping(target = "cantilever", source = "cantileverId")
    @Mapping(target = "steadyArmType", ignore = true) // Gestionado en AfterMapping
    public abstract SteadyArm toEntity(SteadyArmDTO dto);

    @Override
    @Mapping(target = "cantilever", source = "cantileverId")
    @Mapping(target = "steadyArmType", ignore = true) // Gestionado en AfterMapping
    public abstract void updateEntityFromDTO(SteadyArmDTO dto, @MappingTarget SteadyArm entity);

    @AfterMapping
    protected void mapDtoToEntity(SteadyArmDTO dto, @MappingTarget SteadyArm entity) {

        // 2. Resolución del LOV: SteadyArmType (por código)
        if (dto.getSteadyArmType() != null && dto.getSteadyArmType().getCode() != null) {
            entity.setSteadyArmType(
                    masterDataService.getSteadyArmTypeByCode(dto.getSteadyArmType().getCode())
            );
        }

        // 3. Sincronización de la relación 1:1 con Cantilever (Bidireccional)
        // Aseguramos que si existe el padre, la relación sea íntegra
        linkEntity(entity.getCantilever(), entity, Cantilever::setSteadyArm);

    }


    // --- AFTER MAPPING: ENTITY -> DTO (Consulta) ---

    @AfterMapping
    protected void mapEntityToDto(SteadyArm entity, @MappingTarget SteadyArmDTO dto) {

        // 4. Enriquecimiento del DTO con el LOV usando la caché
        if (entity.getSteadyArmType() != null && entity.getSteadyArmType().getId() != null) {
            dto.setSteadyArmType(
                    masterDataService.getSteadyArmTypeByIdAndMapToDTO(entity.getSteadyArmType().getId())
            );
        }
    }
}
