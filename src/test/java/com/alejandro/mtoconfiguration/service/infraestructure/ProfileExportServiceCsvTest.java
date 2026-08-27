package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * Escritura del CSV. Lo que se fija es el contenido —cabecera incluida—, que el directorio se cree
 * si no existe y que el progreso avance fila a fila, que es lo que alimenta el estado del trabajo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileExportServiceCsvTest {

    @Mock
    private IProfileService profileService;
    @Mock
    private ProfileRepository profileRepository;

    @TempDir
    Path tempDir;

    private ProfileExportService service() {
        return new ProfileExportService(profileService, profileRepository,
                new TaskExecutorAdapter(Runnable::run));
    }

    private Profile profile(String id, String kp) {
        Profile profile = new Profile();
        profile.setProfileId(id);
        profile.setKp(new BigDecimal(kp));
        return profile;
    }

    @SuppressWarnings("unchecked")
    private void streamProfiles(List<Profile> profiles) {
        doAnswer(invocation -> {
            Consumer<Profile> consumer = invocation.getArgument(1);
            profiles.forEach(consumer);
            return null;
        }).when(profileService).processProfilesByTrack(eq(1L), any());
    }

    @Test
    @DisplayName("escribe cabecera, filas y crea el directorio que falte")
    void escribeCabeceraYFilas() throws Exception {
        streamProfiles(List.of(profile("P-1", "10.5"), profile("P-2", "11.5")));

        // El directorio no existe: en el arranque de un entorno nuevo nunca existe, y sin
        // createDirectories la exportacion fallaba con un NoSuchFileException poco explicativo.
        Path target = tempDir.resolve("nested").resolve("out.csv");
        ProfileExportService service = service();

        long rows = service.writeCsv(1L, target, service.getBasicMapper(),
                service.resolveHeader("basic"), null);

        assertThat(rows).isEqualTo(2);
        assertThat(target).exists();
        assertThat(Files.readAllLines(target, StandardCharsets.UTF_8))
                .containsExactly("profileId;kp;track", "P-1;10.5;N/A", "P-2;11.5;N/A");
    }

    @Test
    @DisplayName("avisa del progreso una vez por fila")
    void avisaDelProgreso() {
        streamProfiles(List.of(profile("P-1", "1"), profile("P-2", "2"), profile("P-3", "3")));

        AtomicInteger rows = new AtomicInteger();
        ProfileExportService service = service();

        service.writeCsv(1L, tempDir.resolve("out.csv"), service.getBasicMapper(), null,
                profile -> rows.incrementAndGet());

        assertThat(rows.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("sin cabecera cuando no se pide, para no cambiar los ficheros del endpoint antiguo")
    void sinCabeceraSiNoSePide() throws Exception {
        streamProfiles(List.of(profile("P-1", "1")));
        ProfileExportService service = service();

        service.writeCsv(1L, tempDir.resolve("out.csv"), service.getBasicMapper(), null, null);

        assertThat(Files.readAllLines(tempDir.resolve("out.csv"))).containsExactly("P-1;1;N/A");
    }

    @Test
    @DisplayName("un fallo de escritura sale como UncheckedIOException, no como RuntimeException")
    void falloDeEscritura() {
        streamProfiles(List.of(profile("P-1", "1")));
        ProfileExportService service = service();

        // Envolverlo en RuntimeException borraba de que clase de fallo se trataba y obligaba a
        // mirar la causa para distinguir un disco lleno de un error de negocio.
        assertThatThrownBy(() -> service.writeCsv(1L, tempDir.resolve("out.csv"),
                p -> { throw new UncheckedIOException(new java.io.IOException("disco lleno")); }, null, null))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    @DisplayName("resuelve mapper, cabecera y nombre canonico por formato")
    void resuelveFormatos() {
        ProfileExportService service = service();

        assertThat(service.resolveHeader("technical")).isEqualTo("profileId;kp;foundation;poleType");
        assertThat(service.resolveHeader("default")).startsWith("id;profileId;kp;track");
        assertThat(service.resolveHeader("basic")).isEqualTo("profileId;kp;track");

        // Un formato desconocido cae en el basico, igual que hacia el endpoint antiguo, porque el
        // parametro tiene valor por defecto y rechazarlo seria un cambio de comportamiento.
        assertThat(service.resolveMapperName("no-existe")).isEqualTo("basic");
        assertThat(service.resolveMapperName(null)).isEqualTo("basic");
        assertThat(service.resolveMapperName("TECHNICAL")).isEqualTo("technical");
        assertThat(service.resolveMapper("technical")).isNotNull();
    }
}
