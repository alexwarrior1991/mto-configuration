package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.AssemblyConfiguration;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AssemblyConfigurationDTO;
import org.mapstruct.Mapper;

@Mapper(config = CentralConfigMapper.class)
public interface AssemblyConfigurationMapper extends LovMapper<AssemblyConfigurationDTO, AssemblyConfiguration> {
}
