package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
        config = CentralConfigMapper.class,
        uses = {
                ReferenceMapper.class,
                ProfileMapper.class,
                SteadyArmMapper.class
        }
)
public abstract class CantileverMapper implements BaseMapper<CantileverDTO, Cantilever> {

    @Autowired
    protected MasterDataService masterDataService;

    @Override
    @Mapping(target = "profileId", source = "profile.id")
    @Mapping(target = "cantileverType", ignore = true) // Gestionado en AfterMapping
    public abstract CantileverDTO toDTO(Cantilever entity);

    @Override
    @Mapping(target = "profile", source = "profileId")
    @Mapping(target = "cantileverType", ignore = true) // Gestionado en AfterMapping
    public abstract Cantilever toEntity(CantileverDTO dto);

    @Override
    @Mapping(target = "profile", source = "profileId")
    @Mapping(target = "cantileverType", ignore = true) // Gestionado en AfterMapping
    public abstract void updateEntityFromDTO(CantileverDTO dto, @MappingTarget Cantilever entity);

    @AfterMapping
    protected void mapDtoToEntity(CantileverDTO dto, @MappingTarget Cantilever entity) {

        // 2. Resolución de LOV: CantileverType (por código)
        if (dto.getCantileverType() != null && dto.getCantileverType().getCode() != null) {
            entity.setCantileverType(
                    masterDataService.getCantileverTypeByCode(dto.getCantileverType().getCode())
            );
        }

        // 3. Sincronización de la relación 1:1 con SteadyArm (Bidireccional)
        // Usamos linkEntity para asegurar que el SteadyArm apunte correctamente al Cantilever padre
        linkEntity(
                entity.getSteadyArm(),
                entity,
                SteadyArm::setCantilever
        );
    }

    @AfterMapping
    protected void mapEntityToDto(Cantilever entity, @MappingTarget CantileverDTO dto) {
        // 4. Enriquecimiento del DTO con el LOV
        // Usamos el ID de la entidad para obtener el DTO oficial desde la caché del MasterDataService
        if (entity.getCantileverType() != null && entity.getCantileverType().getId() != null) {
            dto.setCantileverType(
                    masterDataService.getCantileverTypeByIdAndMapToDTO(entity.getCantileverType().getId())
            );
        }
    }
}
