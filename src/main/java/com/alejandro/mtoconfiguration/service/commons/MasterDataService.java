package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class MasterDataService {

    private static final String CACHE_ITEM = "mto.semi.configuration.item";

    @Cacheable(value = CACHE_ITEM, keyGenerator = "cacheCustomKeyDTOGenerator", unless = "#result == null ")
    public <E extends BaseEntity, T extends LovDTO> E getByIdOrByCode(T dto, SerializableLongFunction<E> byId, SerializableFunction<String, E> byCode) {

        if (dto == null) {
            return null;
        }

        if (Utils.exists(dto)) {
            return byId.apply(dto.getId());
        }

        if (StringUtils.isBlank(dto.getCode())) {
            return null;
        }

        return byCode.apply(dto.getCode());
    }
}
