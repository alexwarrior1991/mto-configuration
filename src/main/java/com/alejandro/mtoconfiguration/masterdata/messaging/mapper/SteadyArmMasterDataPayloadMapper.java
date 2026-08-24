package com.alejandro.mtoconfiguration.masterdata.messaging.mapper;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.entity.lov.SteadyArmType;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEntityPayloadMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SteadyArm era la unica entidad anotada con {@code @PublishMasterDataEvent} sin
 * mapper, asi que caia en la serializacion generica de la entidad JPA. Y
 * SteadyArm.cantilever <-> Cantilever.steadyArm es bidireccional sin ningun
 * {@code @JsonIgnore}: Jackson entraba en recursion infinita DENTRO de la transaccion
 * de negocio, de modo que cualquier alta, modificacion o baja de un SteadyArm
 * terminaba en error.
 * <p>
 * Del cantilever solo se publica el id, que es lo que rompe el ciclo. Al ser SteadyArm
 * el lado propietario de la relacion (la FK CANTILEVER_ID vive en STEADY_ARM), leer el
 * id del proxy no obliga a inicializarlo.
 */
@Component
public class SteadyArmMasterDataPayloadMapper implements MasterDataEntityPayloadMapper<SteadyArm> {

    @Override
    public Class<SteadyArm> supportedType() {
        return SteadyArm.class;
    }

    @Override
    public Map<String, Object> toPayload(SteadyArm steadyArm) {
        Map<String, Object> values = new LinkedHashMap<>();

        values.put("id", steadyArm.getId());
        values.put("length", steadyArm.getLength());
        values.put("steadyArmType", toSteadyArmTypePayload(steadyArm.getSteadyArmType()));
        values.put("cantileverId", toCantileverId(steadyArm.getCantilever()));

        return values;
    }

    private Map<String, Object> toSteadyArmTypePayload(SteadyArmType steadyArmType) {
        if (steadyArmType == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", steadyArmType.getId());
        values.put("code", steadyArmType.getCode());
        values.put("description", steadyArmType.getDescription());
        return values;
    }

    private Long toCantileverId(Cantilever cantilever) {
        return cantilever != null ? cantilever.getId() : null;
    }
}
