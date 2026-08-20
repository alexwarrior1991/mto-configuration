package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.configuration.cache.CacheNames;
import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LovReferenceResolver {

    /**
     * Resuelve el id de un LOV por su código pasando por caché.
     * Vive en un bean propio para que la llamada desde MasterDataService cruce
     * el proxy de Spring y @Cacheable se aplique de verdad.
     * <p>
     * La clave SpEL es explícita a propósito: con el keyGenerator por defecto el
     * repository se serializaría como objeto arbitrario y la clave sería inestable.
     */
    @Cacheable(
            cacheNames = CacheNames.LOV_ITEM,
            key = "'lovIdByCode:' + #lovName + ':' + #code",
            unless = "#result == null"
    )
    public <E extends Lov> Long resolveIdByCode(String lovName, String code, LovRepository<E> repository) {
        if (StringUtils.isBlank(code)) {
            return null;
        }

        return Optional.ofNullable(repository.findByCode(code))
                .map(entity -> entity.getId())
                .orElse(null);
    }
}
