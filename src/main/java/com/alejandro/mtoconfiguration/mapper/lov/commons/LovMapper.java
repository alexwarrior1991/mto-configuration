package com.alejandro.mtoconfiguration.mapper.lov.commons;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import com.alejandro.mtoconfiguration.utils.Utils;
import org.apache.commons.lang3.StringUtils;

public interface LovMapper<T extends LovDTO, E extends Lov> extends BaseMapper<T, E> {

    default E findOrMap(T dto, LovRepository<E> repository) {
        if (dto == null) {
            return null;
        }

        E entity = null;

        if (Utils.exists(dto)) {
            entity = repository.findById(dto.getId()).orElse(null);
        }

        if (StringUtils.isBlank(dto.getCode())) {
            entity = repository.findByCode(dto.getCode());
        }

        if (entity == null) {
            entity = toEntity(dto);
        }

        return entity;
    }
}
