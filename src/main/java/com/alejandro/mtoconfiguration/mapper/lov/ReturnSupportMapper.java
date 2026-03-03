package com.alejandro.mtoconfiguration.mapper.lov;

import com.alejandro.mtoconfiguration.entity.lov.ReturnSupport;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ReturnSupportDTO;
import org.mapstruct.Mapper;

@Mapper(config = CentralConfigMapper.class)
public interface ReturnSupportMapper extends LovMapper<ReturnSupportDTO, ReturnSupport> {
}
