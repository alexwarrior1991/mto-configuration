package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.mapper.commons.CentralConfigMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.mapper.commons.ToEntityIgnoreAudit;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import org.mapstruct.AfterMapping;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
        config = CentralConfigMapper.class,
        uses = {
                ReferenceMapper.class,
                ProfileMapper.class,
                SteadyArmMapper.class
        }
)
public abstract class CantileverMapper implements BaseMapper<CantileverDTO, Cantilever> {

    @Autowired
    protected MasterDataService masterDataService;

    /**
     * El mapper del brazo, para el 1:1 que se reconcilia a mano en {@code mapDtoToEntity}. Con el
     * sufijo {@code Child} por lo mismo que en {@code ProfileMapper}: el impl generado declara el
     * suyo, privado, como {@code steadyArmMapper}.
     */
    @Autowired
    protected SteadyArmMapper steadyArmChildMapper;

    @Override
    @Mapping(target = "profileId", source = "profile.id")
    @Mapping(target = "cantileverType", ignore = true) // Gestionado en AfterMapping
    public abstract CantileverDTO toDTO(Cantilever entity);

    /**
     * Hereda las reglas de {@code toDTO}: el detalle y el final de un alta o una modificacion
     * vuelcan la entidad con este metodo, no con {@code toDTO} (ver {@code BaseMapper}).
     */
    @Override
    @InheritConfiguration(name = "toDTO")
    public abstract void updateDTOFromEntity(Cantilever entity, @MappingTarget CantileverDTO dto);

    @Override
    @Mapping(target = "profile", source = "profileId")
    @Mapping(target = "cantileverType", ignore = true) // Gestionado en AfterMapping
    @Mapping(target = "steadyArm", ignore = true)      // se reconcilia en mapDtoToEntity
    @ToEntityIgnoreAudit
    public abstract Cantilever toEntity(CantileverDTO dto);

    @Override
    @Mapping(target = "profile", source = "profileId")
    @Mapping(target = "cantileverType", ignore = true) // Gestionado en AfterMapping
    @Mapping(target = "steadyArm", ignore = true)      // se reconcilia en mapDtoToEntity
    @ToEntityIgnoreAudit
    public abstract void updateEntityFromDTO(CantileverDTO dto, @MappingTarget Cantilever entity);

    @AfterMapping
    protected void mapDtoToEntity(CantileverDTO dto, @MappingTarget Cantilever entity) {

        // 2. Resolución de LOV: CantileverType (por código)
        if (dto.getCantileverType() != null) {
            // Vacia (sin codigo y sin id) se ignora: no se puede quitar. Informada, tiene que existir.
            var resolved = resolveLov(dto.getCantileverType(), "cantileverType",
                    masterDataService::getCantileverTypeByCode, masterDataService::getCantileverTypeByIdAndMapToDTO);
            if (resolved != null) {
                entity.setCantileverType(resolved);
            }
        }

        // 3. El brazo, 1:1.
        mergeSteadyArm(dto.getSteadyArm(), entity);
    }

    /**
     * Reconcilia el brazo de la ménsula: mandarlo lo crea o lo actualiza, y {@code null} lo quita
     * (README_API.md §4).
     *
     * <p>A mano, y no con el código generado, por el alta. MapStruct colgaba de la ménsula un
     * {@code new SteadyArm()} vacío y lo rellenaba después, y rellenarlo consulta el catálogo
     * (el tipo de brazo, por código). Con la ménsula ya gestionada, esa consulta dispara el
     * auto-flush de Hibernate, la cascada persiste el brazo a medio rellenar y su {@code INSERT}
     * sale con el tipo a {@code null}: un 409 que dependía de que el catálogo estuviera o no en
     * caché. Aquí el brazo nuevo se construye entero antes de colgarlo.
     *
     * <p>Al modificar, primero la versión: si el brazo que devuelve el cliente está
     * desactualizado, la excepción sale antes de volcar nada sobre él.
     */
    private static final String FIELD_STEADY_ARM = "steadyArm";

    private void mergeSteadyArm(SteadyArmDTO dto, Cantilever entity) {
        SteadyArm current = entity.getSteadyArm();

        if (dto == null) {
            // Quitarlo es borrarlo en logico, como su DELETE directo, y publica su evento
            // (BaseMapper.removeChild). El brazo conserva su mensula sin ocuparla (V30).
            removeChild(current);
            entity.setSteadyArm(null);
            return;
        }

        if (current == null) {
            SteadyArm created = BaseMapper.atField(FIELD_STEADY_ARM, () -> steadyArmChildMapper.toEntity(dto));
            created.setCantilever(entity);
            entity.setSteadyArm(created);
            return;
        }

        checkVersion(dto, current);
        BaseMapper.atField(FIELD_STEADY_ARM, () -> {
            steadyArmChildMapper.updateEntityFromDTO(dto, current);
            return current;
        });
        current.setCantilever(entity);
    }

    @AfterMapping
    protected void mapEntityToDto(Cantilever entity, @MappingTarget CantileverDTO dto) {
        // 4. Enriquecimiento del DTO con el LOV
        // Usamos el ID de la entidad para obtener el DTO oficial desde la caché del MasterDataService
        if (entity.getCantileverType() != null && entity.getCantileverType().getId() != null) {
            dto.setCantileverType(
                    masterDataService.getCantileverTypeByIdAndMapToDTO(entity.getCantileverType().getId())
            );
        }
    }
}
