package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Function;

/**
 * Trabajo de exportacion: vuelca a CSV los perfiles de una via.
 *
 * <p>Solo sabe hacer el trabajo. El estado, los topes de concurrencia y el salto de hilo los pone
 * {@link ProfileJobService}, de modo que esto se puede probar llamandolo directamente, sin
 * executors ni esperas.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileExportJobRunner {

    private final ProfileExportService exportService;
    private final ProfileJobFiles files;

    /** Escribe el CSV y deja en {@code progress} el nombre del fichero y el numero de filas. */
    public void run(UUID jobId, Long trackId, String mapperType, ProfileJobProgress progress) {
        Function<Profile, String> mapper = exportService.resolveMapper(mapperType);
        String header = exportService.resolveHeader(mapperType);

        // El nombre lo pone ProfileJobFiles, que es tambien quien luego lo busca y lo purga: con el
        // patron repartido entre el que escribe y el que limpia, bastaba tocar uno para que la
        // limpieza dejara de reconocer sus propios ficheros.
        String fileName = files.exportFileName(trackId, jobId);
        Path targetPath = files.targetPath(fileName);

        log.info("Exportacion en curso jobId={} trackId={} mapperType={} destino={}",
                jobId, trackId, mapperType, fileName);

        long rows = exportService.writeCsv(trackId, targetPath, mapper, header, profile -> progress.itemSucceeded());

        // El total solo se conoce ahora: la via se recorre por ventanas y contar antes habria sido
        // una consulta extra sobre la misma tabla para adornar un porcentaje.
        progress.setTotalItems((int) Math.min(rows, Integer.MAX_VALUE));
        progress.setOutputFileName(fileName);
    }
}
