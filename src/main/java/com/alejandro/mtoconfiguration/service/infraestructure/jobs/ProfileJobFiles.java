package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Localiza el fichero producido por un trabajo de exportacion.
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

    private final AsyncJobProperties properties;

    /**
     * Fichero del trabajo, si existe y es legible.
     *
     * <p>La normalizacion no es ceremonia: el nombre lo genera la aplicacion hoy, pero esta ruta va
     * a alimentar una descarga, y una comprobacion de contencion es lo unico que garantiza que
     * ningun nombre —ni ahora ni tras un cambio futuro— pueda escaparse del directorio de
     * exportacion con un {@code ../}.</p>
     */
    public Optional<Resource> find(AsyncJob job) {
        String fileName = job.getFileName();

        if (fileName == null || fileName.isBlank()) {
            return Optional.empty();
        }

        Path directory = properties.getProfile().getExportDirectory().toAbsolutePath().normalize();
        Path candidate = directory.resolve(fileName).normalize();

        if (!candidate.startsWith(directory)) {
            log.warn("Nombre de fichero fuera del directorio de exportacion jobId={} fileName={}",
                    job.getId(), fileName);
            return Optional.empty();
        }

        if (!Files.isReadable(candidate)) {
            log.warn("El fichero del trabajo jobId={} ya no esta disponible: {}", job.getId(), fileName);
            return Optional.empty();
        }

        return Optional.of(new FileSystemResource(candidate));
    }
}
