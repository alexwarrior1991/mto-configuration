package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileExportService {

    private final IProfileService profileService;
    private final ProfileRepository profileRepository;
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private static final String CSV_SEPARATOR = ";";

    public CompletableFuture<Void> exportToCsvAsync(Long trackId, Path targetPath, Function<Profile, String> mapper) {
        return CompletableFuture.runAsync(() -> {
            log.info("Hilo virtual iniciando exportación: {}", Thread.currentThread());

            try {
                if (targetPath.getParent() != null) {
                    Files.createDirectories(targetPath.getParent());
                }

                try (BufferedWriter writer = Files.newBufferedWriter(targetPath,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {

                    profileService.processProfilesByTrack(trackId, profile -> {
                        try {
                            writer.write(mapper.apply(profile));
                            writer.newLine();
                        } catch (IOException e) {
                            throw new RuntimeException("Error escribiendo línea CSV", e);
                        }
                    });

                    log.info("Exportación finalizada en {}", targetPath);
                }
            } catch (IOException e) {
                log.error("Error en exportación", e);
                throw new RuntimeException(e);
            }
        }, virtualExecutor);
    }

    /**
     * OPCIÓN B: Sin WindowIterator (Carga todo en memoria)
     * Igual de asíncrono con Virtual Threads, pero sin paginación.
     */
    public CompletableFuture<Void> exportToCsvNoWindowAsync(Long trackId, Path targetPath, Function<Profile, String> mapper) {
        return CompletableFuture.runAsync(() -> {
            log.info("Iniciando exportación SIMPLE (sin ventanas) en hilo virtual: {}", Thread.currentThread());

            try (BufferedWriter writer = Files.newBufferedWriter(targetPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {

                // 1. Recuperamos TODO de golpe (puede ser peligroso con muchos datos)
                List<Profile> profiles = profileRepository.findByTrackId(trackId);

                log.info("Cargados {} perfiles en memoria para exportar", profiles.size());

                // 2. Procesamos la lista de forma funcional
                profiles.forEach(profile -> {
                    try {
                        writer.write(mapper.apply(profile));
                        writer.newLine();
                    } catch (IOException e) {
                        throw new RuntimeException("Error escribiendo línea", e);
                    }
                });

                log.info("Exportación finalizada en {}", targetPath);
            } catch (IOException e) {
                log.error("Fallo crítico en la exportación simple", e);
                throw new RuntimeException(e);
            }
        }, virtualExecutor);
    }


    // --- MAPPERS PREDEFINIDOS ---

    /**
     * Mapper por defecto: Exporta absolutamente todos los campos relevantes del Perfil.
     */
    public Function<Profile, String> getDefaultMapper() {
        return p -> {
            StringJoiner sj = new StringJoiner(CSV_SEPARATOR);
            sj.add(p.getId().toString());
            sj.add(p.getProfileId());
            sj.add(p.getKp().toString());
            sj.add(p.getTrack() != null ? p.getTrack().getName() : "N/A");
            sj.add(p.getProfileStatus() != null ? p.getProfileStatus().getDescription() : "N/A");
            sj.add(p.getFoundation() != null ? p.getFoundation().getDescription() : "N/A");
            sj.add(p.getPoleType() != null ? p.getPoleType().getCode() : "N/A");
            sj.add(p.getPortal() != null ? p.getPortal().getCode() : "N/A");
            sj.add(p.getSectioning() != null ? p.getSectioning().getCode() : "N/A");
            return sj.toString();
        };
    }

    /**
     * Mapper Básico: Solo ID, KP y Nombre de Vía.
     */
    public Function<Profile, String> getBasicMapper() {
        return p -> {
            return p.getProfileId() + CSV_SEPARATOR +
                    p.getKp() + CSV_SEPARATOR +
                    (p.getTrack() != null ? p.getTrack().getName() : "N/A");
        };
    }

    /**
     * Mapper de Infraestructura: Enfocado en Cimentación y Postes.
     */
    public Function<Profile, String> getTechnicalMapper() {
        return p -> {
            StringBuilder sb = new StringBuilder();
            sb.append(p.getProfileId()).append(CSV_SEPARATOR);
            sb.append(p.getKp()).append(CSV_SEPARATOR);
            // Evitamos llamar a MasterDataService aquí, usamos lo que ya tiene la entidad
            sb.append(p.getFoundation() != null ? p.getFoundation().getCode() : "N/A").append(CSV_SEPARATOR);
            sb.append(p.getPoleType() != null ? p.getPoleType().getCode() : "N/A");
            return sb.toString();
        };
    }
}
