package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Todo lo que toca el directorio de exportacion pasa por esta clase, y estas pruebas son la razon:
 * cuando el nombre lo componia el runner y el borrado lo hacia otro, bastaba con que uno cambiara
 * el patron para que la limpieza dejara de reconocer sus propios ficheros y el disco creciera sin
 * que nada fallara.
 */
class ProfileJobFilesTest {

    private static final UUID JOB_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @TempDir
    Path tempDir;

    private ProfileJobFiles files;

    @BeforeEach
    void setUp() {
        AsyncJobProperties properties = new AsyncJobProperties();
        properties.getProfile().setExportDirectory(tempDir);
        files = new ProfileJobFiles(properties);
    }

    private AsyncJob jobWithFile(String fileName) {
        AsyncJob job = new AsyncJob();
        job.setId(JOB_ID);
        job.setFileName(fileName);
        return job;
    }

    private Path writeFile(String name, Instant lastModified) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, "profileId;kp;track\n");
        Files.setLastModifiedTime(path, FileTime.from(lastModified));
        return path;
    }

    @Test
    @DisplayName("el nombre lleva via y trabajo, para que dos exportaciones no se pisen")
    void elNombreIdentificaAlTrabajo() {
        String fileName = files.exportFileName(42L, JOB_ID);

        // Sin el identificador, dos exportaciones de la misma via compartirian fichero y la segunda
        // dejaria a la primera devolviendo una descarga que ya no es la suya.
        assertThat(fileName).isEqualTo("profiles-track-42-" + JOB_ID + ".csv");
        assertThat(files.targetPath(fileName)).isEqualTo(tempDir.resolve(fileName).toAbsolutePath());
    }

    @Test
    @DisplayName("encuentra el fichero de un trabajo y lo pierde cuando ya no esta")
    void encuentraYPierde() throws Exception {
        String fileName = files.exportFileName(42L, JOB_ID);
        writeFile(fileName, Instant.now());

        assertThat(files.find(jobWithFile(fileName))).isPresent();

        files.delete(fileName);
        assertThat(files.find(jobWithFile(fileName))).isEmpty();
    }

    @Test
    @DisplayName("un nombre que se sale del directorio no se sirve nunca")
    void nombreQueSeEscapa() {
        // Hoy el nombre lo genera la aplicacion, asi que esto no puede pasar. La comprobacion esta
        // porque esta ruta alimenta una descarga: es lo unico que garantiza que siga sin poder
        // pasar despues de un cambio que nadie relacione con la seguridad.
        assertThat(files.find(jobWithFile("../../etc/passwd"))).isEmpty();
        assertThat(files.find(jobWithFile(null))).isEmpty();
        assertThat(files.find(jobWithFile("  "))).isEmpty();
    }

    @Test
    @DisplayName("barre los CSV huerfanos viejos y respeta los recientes")
    void barreHuerfanos() throws Exception {
        Instant threshold = Instant.now().minus(Duration.ofDays(7));

        // El huerfano tipico: una exportacion que fallo a mitad. Su nombre NUNCA llego a guardarse
        // —solo se guarda cuando el volcado termina bien— asi que ninguna fila lo referencia y sin
        // esta pasada se quedaria en disco para siempre.
        Path viejo = writeFile(files.exportFileName(1L, UUID.randomUUID()), threshold.minus(Duration.ofDays(1)));
        Path reciente = writeFile(files.exportFileName(2L, UUID.randomUUID()), Instant.now());

        assertThat(files.deleteOrphansOlderThan(threshold)).isEqualTo(1);
        assertThat(viejo).doesNotExist();
        assertThat(reciente).exists();
    }

    @Test
    @DisplayName("no toca ficheros que no ha generado esta aplicacion")
    void noTocaLoAjeno() throws Exception {
        Instant threshold = Instant.now().minus(Duration.ofDays(7));
        Path ajeno = writeFile("informe-anual.csv", threshold.minus(Duration.ofDays(30)));

        // Una limpieza que borra lo que no reconoce es una limpieza que un dia se lleva algo que
        // importaba, y el directorio de exportacion puede estar compartido.
        assertThat(files.deleteOrphansOlderThan(threshold)).isZero();
        assertThat(ajeno).exists();
    }

    @Test
    @DisplayName("un directorio que no existe todavia no es un error")
    void directorioInexistente() {
        AsyncJobProperties properties = new AsyncJobProperties();
        properties.getProfile().setExportDirectory(tempDir.resolve("todavia-no"));

        // Pasa en cada entorno nuevo hasta la primera exportacion, y la purga corre antes.
        assertThat(new ProfileJobFiles(properties).deleteOrphansOlderThan(Instant.now())).isZero();
    }
}
