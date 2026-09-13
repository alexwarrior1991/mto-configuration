package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Dueño del directorio de exportacion: nombra, localiza y borra los CSV de los trabajos.
 *
 * <p>Todo lo que toca ese directorio pasa por aqui a proposito. Cuando el nombre lo componia el
 * runner y el borrado lo hacia otro, bastaba con que uno de los dos cambiara el patron para que la
 * limpieza dejara de reconocer sus propios ficheros y el disco creciera en silencio.</p>
 *
 * <p>La ruta se reconstruye a partir del directorio configurado y del nombre guardado, en lugar de
 * guardar la ruta entera en la fila. Dos motivos: el directorio es configuracion del entorno y no
 * un dato del trabajo —si el despliegue lo cambia, las filas viejas siguen sirviendo—, y una ruta
 * absoluta persistida es una ruta que alguien puede acabar devolviendo al cliente.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileJobFiles {

    private static final String EXPORT_PREFIX = "profiles-track-";
    private static final String EXPORT_SUFFIX = ".csv";

    private final AsyncJobProperties properties;

    /**
     * Nombre del CSV de un trabajo de exportacion.
     *
     * <p>Lleva el identificador del trabajo, y no es decorativo: sin el, dos exportaciones de la
     * misma via se pisarian el fichero y la segunda dejaria a la primera devolviendo una descarga
     * que ya no es la suya.</p>
     */
    public String exportFileName(Long trackId, UUID jobId) {
        return EXPORT_PREFIX + trackId + "-" + jobId + EXPORT_SUFFIX;
    }

    /** Ruta de destino de un fichero recien nombrado. */
    public Path targetPath(String fileName) {
        return directory().resolve(fileName);
    }

    /** Fichero del trabajo, si existe y es legible. */
    public Optional<Resource> find(AsyncJob job) {
        return resolve(job.getFileName())
                .filter(path -> {
                    if (Files.isReadable(path)) {
                        return true;
                    }

                    log.warn("El fichero del trabajo jobId={} ya no esta disponible: {}",
                            job.getId(), job.getFileName());
                    return false;
                })
                .map(FileSystemResource::new);
    }

    /** Borra el CSV de un trabajo purgado. Silencioso si ya no estaba. */
    public void delete(String fileName) {
        delete(JobType.PROFILE_EXPORT, fileName);
    }

    /**
     * Borra el fichero de un trabajo purgado, sea del tipo que sea.
     *
     * <p>Hace falta el tipo porque <b>no todos los ficheros viven en el mismo directorio</b>: las
     * exportaciones dejan su CSV en {@code export-directory} y las dos importaciones dejan su
     * informe JSON en el suyo. Borrando todo contra el directorio de exportacion, los informes no
     * se borraban nunca y el disco crecia en silencio, que es exactamente el fallo que esta clase
     * existe para evitar.
     */
    public void delete(JobType type, String fileName) {
        resolve(directoryOf(type), fileName).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                // No se propaga: el fichero es lo accesorio y la fila es lo que hay que borrar. Si
                // el borrado del fichero falla, la siguiente pasada lo reintenta.
                log.warn("No se ha podido borrar el fichero {} del trabajo tipo {}", fileName, type, e);
            }
        });
    }

    /**
     * Directorio en el que deja su fichero cada tipo de trabajo.
     *
     * <p>Los tipos que no producen ninguno caen en el de exportacion, y da igual: la purga solo
     * llega aqui con un nombre de fichero guardado, y esos no lo tienen.
     */
    private Path directoryOf(JobType type) {
        return switch (type) {
            case LOV_IMPORT -> properties.getLov().getReportDirectory().toAbsolutePath().normalize();
            case PROFILE_IMPORT -> properties.getProfile().getImportReportDirectory()
                    .toAbsolutePath().normalize();
            default -> directory();
        };
    }

    /**
     * Borra los CSV huerfanos anteriores al umbral.
     *
     * <p>Los huerfanos existen de verdad: una exportacion que falla a mitad deja un CSV escrito a
     * medias que <b>ninguna fila referencia</b>, porque el nombre solo se guarda cuando el volcado
     * termina bien. Sin esta pasada, cada exportacion fallida deja megas en disco para siempre.</p>
     *
     * <p>Solo se tocan ficheros con el patron que genera esta aplicacion: el directorio podria
     * contener otras cosas, y una limpieza que borre lo que no reconoce es una limpieza que un dia
     * se lleva algo que importaba. El umbral es de dias, asi que no hay forma de pillar un fichero
     * que se este escribiendo ahora.</p>
     */
    public int deleteOrphansOlderThan(Instant threshold) {
        Path directory = directory();

        if (!Files.isDirectory(directory)) {
            return 0;
        }

        try (Stream<Path> files = Files.list(directory)) {
            return (int) files
                    .filter(Files::isRegularFile)
                    .filter(this::isExportFile)
                    .filter(path -> isOlderThan(path, threshold))
                    .peek(this::deleteQuietly)
                    .count();
        } catch (IOException e) {
            log.warn("No se ha podido recorrer el directorio de exportacion {}", directory, e);
            return 0;
        }
    }

    /**
     * Ruta del fichero dentro del directorio de exportacion, si el nombre es utilizable.
     *
     * <p>La comprobacion de contencion no es ceremonia: esta ruta alimenta una descarga, y es lo
     * unico que garantiza que ningun nombre —ni ahora, que lo genera la aplicacion, ni tras un
     * cambio futuro— pueda escaparse del directorio con un {@code ../}.</p>
     */
    private Optional<Path> resolve(String fileName) {
        return resolve(directory(), fileName);
    }

    private Optional<Path> resolve(Path directory, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return Optional.empty();
        }

        Path candidate = directory.resolve(fileName).normalize();

        if (!candidate.startsWith(directory)) {
            log.warn("Nombre de fichero fuera de su directorio: {}", fileName);
            return Optional.empty();
        }

        return Optional.of(candidate);
    }

    private Path directory() {
        return properties.getProfile().getExportDirectory().toAbsolutePath().normalize();
    }

    private boolean isExportFile(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(EXPORT_PREFIX) && name.endsWith(EXPORT_SUFFIX);
    }

    private boolean isOlderThan(Path path, Instant threshold) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isBefore(threshold);
        } catch (IOException e) {
            // Si no se puede saber su edad, se deja: borrar por si acaso es peor que no borrar.
            log.warn("No se ha podido leer la fecha del fichero {}", path, e);
            return false;
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
            log.info("Borrado CSV de exportacion huerfano: {}", path.getFileName());
        } catch (IOException e) {
            log.warn("No se ha podido borrar el fichero huerfano {}", path, e);
        }
    }
}
