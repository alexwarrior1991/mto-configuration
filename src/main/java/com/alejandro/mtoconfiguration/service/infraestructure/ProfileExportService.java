package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.lov.Sectioning;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import java.util.function.Function;

@Service
@Slf4j
public class ProfileExportService {

    private static final String CSV_SEPARATOR = ";";

    /** Formatos de columnas admitidos por {@link #resolveMapper(String)}. */
    public static final String MAPPER_BASIC = "basic";
    public static final String MAPPER_DEFAULT = "default";
    public static final String MAPPER_TECHNICAL = "technical";

    private final IProfileService profileService;

    public ProfileExportService(IProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Escribe el CSV de una via de forma <b>sincrona</b>, en el hilo que llama.
     *
     * <p>Es el nucleo de la exportacion y no salta de hilo: quien la llama, la capa de trabajos
     * ({@code POST /profiles/jobs/export}), ya corre en segundo plano, con su estado, su cupo y su
     * descarga.</p>
     *
     * <p>Recorre la via con {@code processProfilesByTrack}, es decir, por ventanas y desacoplando
     * cada entidad del contexto de persistencia. Cargar la via entera en una lista funciona hasta
     * el dia en que la via es grande, y entonces no falla despacio: falla con un OutOfMemoryError
     * que se lleva por delante toda la aplicacion, no solo la exportacion.</p>
     *
     * @param header      cabecera del CSV, o {@code null} para no escribir ninguna
     * @param rowCallback aviso por cada fila escrita, para llevar el progreso. Puede ser
     *                    {@code null}
     * @return filas de datos escritas, sin contar la cabecera
     */
    public long writeCsv(Long trackId,
                         Path targetPath,
                         Function<Profile, String> mapper,
                         String header,
                         Consumer<Profile> rowCallback) {
        createParentDirectories(targetPath);

        long[] rows = {0};

        try (BufferedWriter writer = Files.newBufferedWriter(targetPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            if (header != null) {
                writer.write(header);
                writer.newLine();
            }

            profileService.processProfilesByTrack(trackId, profile -> {
                try {
                    writer.write(mapper.apply(profile));
                    writer.newLine();
                } catch (IOException e) {
                    // UncheckedIOException y no RuntimeException: el consumidor no puede declarar
                    // excepciones comprobadas, pero envolverlo en la excepcion generica borraba de
                    // que clase de fallo se trataba y obligaba a quien lo captura a mirar la causa.
                    throw new UncheckedIOException("Error escribiendo linea CSV de la via " + trackId, e);
                }

                rows[0]++;

                if (rowCallback != null) {
                    rowCallback.accept(profile);
                }
            });

            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Error exportando la via " + trackId + " a " + targetPath, e);
        }

        log.info("Exportacion escrita en {} ({} filas)", targetPath, rows[0]);
        return rows[0];
    }

    /**
     * Mapper correspondiente al formato pedido; el basico si el nombre no se reconoce.
     *
     * <p>Se elige tolerar un nombre desconocido en lugar de rechazarlo porque el parametro tiene
     * valor por defecto y asi se comportaba el antiguo {@code GET /profiles/track/{id}/export}.</p>
     */
    public Function<Profile, String> resolveMapper(String mapperType) {
        return switch (normalize(mapperType)) {
            case MAPPER_TECHNICAL -> getTechnicalMapper();
            case MAPPER_DEFAULT -> getDefaultMapper();
            default -> getBasicMapper();
        };
    }

    /** Cabecera del CSV para el formato pedido. */
    public String resolveHeader(String mapperType) {
        return switch (normalize(mapperType)) {
            case MAPPER_TECHNICAL -> String.join(CSV_SEPARATOR,
                    "profileId", "kp", "foundation", "poleType");
            case MAPPER_DEFAULT -> String.join(CSV_SEPARATOR,
                    "id", "profileId", "kp", "track", "profileStatus", "foundation",
                    "poleType", "portal", "sectionings");
            default -> String.join(CSV_SEPARATOR, "profileId", "kp", "track");
        };
    }

    /** Nombre canonico del formato, para guardarlo en el trabajo y devolverlo en la respuesta. */
    public String resolveMapperName(String mapperType) {
        String normalized = normalize(mapperType);

        return switch (normalized) {
            case MAPPER_TECHNICAL, MAPPER_DEFAULT -> normalized;
            default -> MAPPER_BASIC;
        };
    }

    private String normalize(String mapperType) {
        return mapperType == null ? MAPPER_BASIC : mapperType.toLowerCase();
    }

    private void createParentDirectories(Path targetPath) {
        Path parent = targetPath.getParent();

        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el directorio de exportacion " + parent, e);
        }
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
            // Una sola columna con todos los codigos: anadir columnas la haria depender del
            // perfil con mas seccionamientos, y el CSV dejaria de tener ancho fijo.
            sj.add(p.getSectionings() == null || p.getSectionings().isEmpty() ? "N/A"
                    : p.getSectionings().stream().map(each -> each.getCode())
                            .collect(Collectors.joining(" ")));
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
