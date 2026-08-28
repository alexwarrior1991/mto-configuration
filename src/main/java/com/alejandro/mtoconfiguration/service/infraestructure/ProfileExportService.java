package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.configuration.AsyncConfiguration;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
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
    private final ProfileRepository profileRepository;

    /**
     * Executor de la aplicacion, con hilos virtuales y propagacion del {@code SecurityContext}.
     *
     * <p>Antes esta clase creaba su propio {@code Executors.newVirtualThreadPerTaskExecutor()}. Eso
     * tenia dos problemas que no se ven hasta que muerden: el executor no se cerraba nunca, y sobre
     * todo la exportacion corria <b>sin identidad</b>, porque el {@code SecurityContext} vive en un
     * ThreadLocal y un hilo virtual recien creado arranca vacio. La auditoria de todo lo que tocara
     * esa rama quedaba atribuida a «system» en silencio.</p>
     */
    private final AsyncTaskExecutor taskExecutor;

    public ProfileExportService(
            IProfileService profileService,
            ProfileRepository profileRepository,
            @Qualifier(AsyncConfiguration.TASK_EXECUTOR) AsyncTaskExecutor taskExecutor
    ) {
        this.profileService = profileService;
        this.profileRepository = profileRepository;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Escribe el CSV de una via de forma <b>sincrona</b>, en el hilo que llama.
     *
     * <p>Es el nucleo de la exportacion, y esta separado de las variantes asincronas justamente
     * para que quien ya corre en segundo plano —la capa de trabajos— no tenga que envolver su
     * trabajo en otro salto de hilo solo para reaprovechar el codigo de escritura.</p>
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
     * Exportacion en segundo plano que devuelve un {@code CompletableFuture}.
     *
     * <p>Sigue existiendo tal cual porque la usa el endpoint {@code GET /profiles/track/{id}/export},
     * y su comportamiento no ha cambiado: mismo fichero, mismas columnas y <b>sin cabecera</b>, que
     * es lo que esperan los consumidores que ya leen esos ficheros.</p>
     *
     * <p>Para operaciones nuevas es preferible la capa de trabajos
     * ({@code POST /profiles/jobs/export}): aquella deja rastro del estado, controla la
     * concurrencia y ofrece la descarga; esta no sabe decir si termino ni si fallo.</p>
     */
    public CompletableFuture<Void> exportToCsvAsync(Long trackId, Path targetPath, Function<Profile, String> mapper) {
        return CompletableFuture.runAsync(() -> {
            log.info("Hilo virtual iniciando exportación: {}", Thread.currentThread());
            writeCsv(trackId, targetPath, mapper, null, null);
        }, taskExecutor);
    }

    /**
     * Variante sin ventanas: carga la via <b>entera</b> en memoria antes de escribirla.
     *
     * @deprecated No usar en produccion, y no usar en la capa de trabajos. Es material didactico:
     * sirve para contrastar contra {@link #writeCsv} lo que cuesta prescindir de la paginacion. El
     * consumo de memoria crece con el tamano de la via, de modo que el fallo no llega cuando la
     * exportacion es lenta, sino de golpe y en forma de OutOfMemoryError que tumba el proceso
     * completo. Se conserva unicamente para no romper a quien ya la llame.
     */
    @Deprecated(since = "capa de trabajos asincronos", forRemoval = false)
    public CompletableFuture<Void> exportToCsvNoWindowAsync(Long trackId, Path targetPath, Function<Profile, String> mapper) {
        return CompletableFuture.runAsync(() -> {
            log.info("Iniciando exportación SIMPLE (sin ventanas) en hilo virtual: {}", Thread.currentThread());

            createParentDirectories(targetPath);

            try (BufferedWriter writer = Files.newBufferedWriter(targetPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {

                List<Profile> profiles = profileRepository.findByTrackId(trackId);

                log.info("Cargados {} perfiles en memoria para exportar", profiles.size());

                profiles.forEach(profile -> {
                    try {
                        writer.write(mapper.apply(profile));
                        writer.newLine();
                    } catch (IOException e) {
                        throw new UncheckedIOException("Error escribiendo linea CSV de la via " + trackId, e);
                    }
                });

                log.info("Exportación finalizada en {}", targetPath);
            } catch (IOException e) {
                log.error("Fallo crítico en la exportación simple", e);
                throw new UncheckedIOException("Error exportando la via " + trackId + " a " + targetPath, e);
            }
        }, taskExecutor);
    }

    /**
     * Mapper correspondiente al formato pedido; el basico si el nombre no se reconoce.
     *
     * <p>Se elige tolerar un nombre desconocido en lugar de rechazarlo porque el parametro tiene
     * valor por defecto y ya se comportaba asi en el endpoint antiguo.</p>
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
                    "poleType", "portal", "sectioning");
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
