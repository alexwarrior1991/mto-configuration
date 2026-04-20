package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ToEntityIgnoreAudit;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
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
public abstract class DisconnectorMapper implements BaseMapper<DisconnectorDTO, Disconnector> {

    @Autowired
    protected MasterDataService masterDataService;

    @Override
    @Mapping(target = "stationId", source = "station.id")
    @Mapping(target = "profileId", source = "profile.id")
    @Mapping(target = "disconnectorFunction", ignore = true)
    public abstract DisconnectorDTO toDTO(Disconnector entity);

    @Override
    @Mapping(target = "station", source = "stationId")
    @Mapping(target = "profile", source = "profileId")
    @Mapping(target = "disconnectorFunction", ignore = true)
    @ToEntityIgnoreAudit
    public abstract Disconnector toEntity(DisconnectorDTO dto);

    @Override
    @Mapping(target = "station", source = "stationId")
    @Mapping(target = "profile", source = "profileId")
    @Mapping(target = "disconnectorFunction", ignore = true)
    @ToEntityIgnoreAudit
    public abstract void updateEntityFromDTO(DisconnectorDTO dto, @MappingTarget Disconnector entity);

    @AfterMapping
    protected void mapDtoToEntity(DisconnectorDTO dto, @MappingTarget Disconnector entity) {

        if (dto.getDisconnectorFunction() != null && dto.getDisconnectorFunction().getCode() != null) {
            entity.setDisconnectorFunction(
                    masterDataService.getDisconnectorFunctionByCode(dto.getDisconnectorFunction().getCode())
            );
        }

        // 3. Sincronización de otras relaciones si existieran
    }

    @AfterMapping
    protected void mapEntityToDto(Disconnector entity, @MappingTarget DisconnectorDTO dto) {
        if (entity.getDisconnectorFunction() != null && entity.getDisconnectorFunction().getId() != null) {
            dto.setDisconnectorFunction(
                    masterDataService.getDisconnectorFunctionByIdAndMapToDTO(entity.getDisconnectorFunction().getId())
            );
        }
    }
}
