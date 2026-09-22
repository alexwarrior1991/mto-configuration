package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulatorSwitch;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ToEntityIgnoreAudit;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorSwitchDTO;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(
        config = CentralConfigMapper.class,
        uses = {
                ReferenceMapper.class
        }
)
public abstract class SectionInsulatorSwitchMapper
        implements BaseMapper<SectionInsulatorSwitchDTO, SectionInsulatorSwitch> {

    @Override
    @Mapping(target = "trackId", source = "track.id")
    public abstract SectionInsulatorSwitchDTO toDTO(SectionInsulatorSwitch entity);

    /**
     * Hereda las reglas de {@code toDTO}: el detalle y el final de un alta o una modificacion
     * vuelcan la entidad con este metodo, no con {@code toDTO} (ver {@code BaseMapper}).
     */
    @Override
    @InheritConfiguration(name = "toDTO")
    public abstract void updateDTOFromEntity(SectionInsulatorSwitch entity, @MappingTarget SectionInsulatorSwitchDTO dto);

    /**
     * {@code sectionInsulator} se ignora a propósito: el lado inverso lo pone el padre, en el
     * {@code mergeCollection} de {@code SectionInsulatorMapper}. Dejar que MapStruct lo resolviera
     * aquí obligaría a que el DTO hijo llevase el id del padre, que en un alta anidada todavía no
     * existe.
     */
    @Override
    @Mapping(target = "track", source = "trackId")
    @Mapping(target = "sectionInsulator", ignore = true)
    @ToEntityIgnoreAudit
    public abstract SectionInsulatorSwitch toEntity(SectionInsulatorSwitchDTO dto);

    @Override
    @Mapping(target = "track", source = "trackId")
    @Mapping(target = "sectionInsulator", ignore = true)
    @ToEntityIgnoreAudit
    public abstract void updateEntityFromDTO(SectionInsulatorSwitchDTO dto,
                                             @MappingTarget SectionInsulatorSwitch entity);
}
