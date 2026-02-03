package com.alejandro.mtoconfiguration.mapper.commons;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import org.mapstruct.*;

@MapperConfig(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED
)
public interface CentralConfigMapper {

    @Mapping(target = "createDate", ignore = true)
    @Mapping(target = "createUser", ignore = true)
    @Mapping(target = "versionDate", ignore = true)
    @Mapping(target = "versionUser", ignore = true)
    @Mapping(target = "versionNumber", ignore = true)
    @Mapping(target = "versionIncremented", ignore = true)
    @Mapping(target = "dirty", ignore = true)
    void toEntityIgnoreAudit(BaseDTO dto, @MappingTarget BaseEntity entity);
}
