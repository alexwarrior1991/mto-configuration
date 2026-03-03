package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.DisconnectorFunction;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import org.mapstruct.Mapper;

@Mapper(config = CentralConfigMapper.class)
public interface DisconnectorFunctionMapper extends LovMapper<DisconnectorFunctionDTO, DisconnectorFunction> {
}
