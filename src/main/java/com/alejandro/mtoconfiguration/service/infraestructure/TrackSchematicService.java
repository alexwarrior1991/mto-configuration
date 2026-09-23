package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.configuration.cache.CacheNames;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.schematic.TrackSchematicDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.schematic.TrackSchematicDTO.CantileverArm;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.schematic.TrackSchematicDTO.InsulatorMark;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.schematic.TrackSchematicDTO.ProfileNode;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.CantileverRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository.ProfileSectioningCode;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * El esquema de una via: la proyeccion que devuelve {@code GET /tracks/{id}/schematic}
 * (README_API.md §6), servida desde Redis.
 *
 * <p>Es la unica lectura de infraestructura que se cachea con sus hijos, y va contra la regla que
 * documenta {@code BaseService.isCacheable()} a proposito. Aquella regla existe porque la
 * invalidacion es por servicio y un DTO con hijos se vaciaria con cualquier escritura del sistema;
 * aqui eso se asume: la clave es por via ({@code TrackSchematicService:getSchematic:<id>}, en
 * {@code normal:item}) y {@code RedisCacheEvictService.evictTrackSchematics()} la barre entera tras
 * cualquier escritura de infraestructura o de catalogo ({@code CacheEvictionListener},
 * {@code LovCacheEvictionListener}, siempre {@code AFTER_COMMIT}), porque el evento solo trae el
 * nombre del servicio que escribio, no la via. Lo que se gana es que abrir el esquema de la misma
 * via dos veces seguidas, lo normal al consultarlo, no vuelva a montar 600 perfiles con sus
 * ménsulas.
 *
 * <p>No es un {@code CRUDService}: no extiende {@code BaseService}, no publica eventos y no hereda
 * {@code condition = "#root.target.cacheable"}. Va en su propio bean, y no dentro de
 * {@code TrackService}, para que la clave lleve su propio nombre: el patron de invalidacion de
 * {@code TrackService} esta anclado con {@code :} y no lo alcanza (ni debe: lo vacian los ocho).
 *
 * <p>Cinco consultas por via, todas por {@code track_id} y sin N+1: la cabecera, los perfiles en
 * su orden fisico con lo a-uno, las ménsulas de toda la via, los codigos de seccionamiento y los
 * aisladores. Las tres colecciones se agrupan aqui por id de perfil.
 */
@Service
@RequiredArgsConstructor
public class TrackSchematicService {

    private static final Logger log = LoggerFactory.getLogger(TrackSchematicService.class);

    private final TrackRepository trackRepository;
    private final ProfileRepository profileRepository;
    private final CantileverRepository cantileverRepository;
    private final SectionInsulatorRepository sectionInsulatorRepository;

    /**
     * @throws NotFoundException si la via no existe o esta borrada (404 {@code NOT-001}); no se
     *                           cachea nada en ese caso.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.NORMAL_ITEM,
            keyGenerator = "redisCacheKeyGenerator",
            unless = "#result == null"
    )
    public TrackSchematicDTO getSchematic(Long trackId) {
        Track track = trackRepository.findForSchematic(trackId)
                .orElseThrow(() -> new NotFoundException("Track not found with id " + trackId));

        Map<Long, List<CantileverArm>> arms = new HashMap<>();
        for (Cantilever cantilever : cantileverRepository.findForSchematic(trackId)) {
            arms.computeIfAbsent(cantilever.getProfile().getId(), id -> new ArrayList<>())
                    .add(CantileverArm.of(cantilever));
        }

        Map<Long, List<String>> sectionings = new HashMap<>();
        for (ProfileSectioningCode sectioning : profileRepository.findSectioningCodesForSchematic(trackId)) {
            sectionings.computeIfAbsent(sectioning.getProfileId(), id -> new ArrayList<>())
                    .add(sectioning.getCode());
        }

        List<ProfileNode> profiles = profileRepository.findForSchematic(trackId).stream()
                .map(profile -> ProfileNode.of(profile,
                        arms.getOrDefault(profile.getId(), new ArrayList<>()),
                        sectionings.getOrDefault(profile.getId(), new ArrayList<>())))
                .collect(Collectors.toCollection(ArrayList::new));

        List<InsulatorMark> insulators = sectionInsulatorRepository.findForSchematic(trackId).stream()
                .map(InsulatorMark::of)
                .collect(Collectors.toCollection(ArrayList::new));

        log.debug("Esquema de la via {}: {} perfiles, {} aisladores", trackId, profiles.size(), insulators.size());
        return TrackSchematicDTO.of(track, profiles, insulators);
    }
}
