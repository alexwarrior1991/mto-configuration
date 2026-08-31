package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Monta el grafo completo de mappers generados, ya cableado entre si.
 *
 * <p>Fuera de Spring hay que enchufar a mano lo que en produccion inyecta el contenedor, y cada
 * mapper depende de otros dos o tres, asi que hacerlo en cada test se convertia en una copia larga
 * y facil de dejar incompleta: bastaba con olvidar un campo para que una referencia a metodo
 * explotara con un {@code NullPointerException} tres niveles mas abajo.
 *
 * <p>Ademas hay que enchufar <b>dos</b> campos por cada mapper hijo. El impl que genera MapStruct
 * declara el suyo privado con el nombre corto ({@code trackMapper}) y la clase abstracta declara
 * el que usa la reconciliacion con sufijo {@code Child} ({@code trackChildMapper}). Spring inyecta
 * los dos; por reflexion hay que hacer lo propio.
 */
final class MapperGraph {

    private final MasterDataService masterDataService;
    private final ReferenceMapper referenceMapper;

    final SteadyArmMapperImpl steadyArm = new SteadyArmMapperImpl();
    final CantileverMapperImpl cantilever = new CantileverMapperImpl();
    final DisconnectorMapperImpl disconnector = new DisconnectorMapperImpl();
    final SectionInsulatorMapperImpl sectionInsulator = new SectionInsulatorMapperImpl();
    final ProfileMapperImpl profile = new ProfileMapperImpl();
    final TrackMapperImpl track = new TrackMapperImpl();
    final StationMapperImpl station = new StationMapperImpl();
    final ExecutionPackageMapperImpl executionPackage = new ExecutionPackageMapperImpl();

    MapperGraph(MasterDataService masterDataService, ReferenceMapper referenceMapper) {
        this.masterDataService = masterDataService;
        this.referenceMapper = referenceMapper;

        base(steadyArm);
        base(cantilever);
        base(disconnector);
        base(sectionInsulator);
        base(profile);
        base(track);
        base(station);
        base(executionPackage);

        child(cantilever, "steadyArmMapper", steadyArm);

        child(profile, "cantileverMapper", cantilever);
        child(profile, "disconnectorMapper", disconnector);

        child(track, "profileMapper", profile);

        child(station, "trackMapper", track);
        child(station, "disconnectorMapper", disconnector);
        child(station, "sectionInsulatorMapper", sectionInsulator);

        child(executionPackage, "trackMapper", track);
        child(executionPackage, "stationMapper", station);
    }

    /** Colaboraciones que tiene todo mapper: el resolutor de referencias y el catalogo de LOV. */
    private void base(Object mapper) {
        setIfPresent(mapper, "referenceMapper", referenceMapper);
        setIfPresent(mapper, "masterDataService", masterDataService);
    }

    /**
     * Enchufa el mapper hijo en sus dos campos: el corto del impl generado y el {@code Child} de
     * la clase abstracta, que es el que usa la reconciliacion del {@code @AfterMapping}.
     */
    private void child(Object parent, String shortName, Object childMapper) {
        setIfPresent(parent, shortName, childMapper);
        setIfPresent(parent, shortName.replace("Mapper", "ChildMapper"), childMapper);
    }

    /** No todos los mappers declaran todos los campos; los que faltan simplemente no se tocan. */
    private static void setIfPresent(Object target, String field, Object value) {
        if (org.springframework.util.ReflectionUtils.findField(target.getClass(), field) != null) {
            ReflectionTestUtils.setField(target, field, value);
        }
    }
}
