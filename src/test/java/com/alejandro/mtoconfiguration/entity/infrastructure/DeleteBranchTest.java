package com.alejandro.mtoconfiguration.entity.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hasta donde baja cada forma de borrar una entidad ({@code CRUDEntity}).
 *
 * <p>{@code deleteWithOwnChildren} es el {@code DELETE} directo y quitar un hijo del {@code PUT} de
 * su padre: se lleva lo que no existe sin la entidad y nada mas, porque lo demas que cuelga impide
 * borrarla ({@code DeletionGuard}, 409). {@code deleteCascade} es la rama entera, que solo usa el
 * borrado de una via con {@code ?cascade=true}.
 */
@DisplayName("Lo que se lleva una entidad al borrarse")
class DeleteBranchTest {

    private static Profile perfilConMensulaYBrazo() {
        Profile profile = new Profile();
        Cantilever cantilever = new Cantilever();
        SteadyArm arm = new SteadyArm();
        cantilever.setSteadyArm(arm);
        profile.addCantilever(cantilever);
        return profile;
    }

    @Test
    @DisplayName("un perfil se lleva sus mensulas y los brazos de ellas")
    void perfil() {
        Profile profile = perfilConMensulaYBrazo();

        profile.deleteWithOwnChildren();

        assertThat(profile.isDeleted()).isTrue();
        assertThat(profile.getCantilevers().getFirst().isDeleted()).isTrue();
        assertThat(profile.getCantilevers().getFirst().getSteadyArm().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("un aislador se lleva sus agujas")
    void aislador() {
        SectionInsulator insulator = new SectionInsulator();
        SectionInsulatorSwitch insulatorSwitch = new SectionInsulatorSwitch();
        insulator.getSwitches().add(insulatorSwitch);

        insulator.deleteWithOwnChildren();

        assertThat(insulatorSwitch.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("una via no se lleva sus perfiles, salvo que se borre la rama entera")
    void via() {
        Track track = new Track();
        Profile profile = perfilConMensulaYBrazo();
        track.addProfile(profile);

        track.deleteWithOwnChildren();

        assertThat(track.isDeleted()).isTrue();
        assertThat(profile.isDeleted()).isFalse();

        track.deleteCascade();

        assertThat(profile.isDeleted()).isTrue();
        assertThat(profile.getCantilevers().getFirst().getSteadyArm().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("una estacion no se lleva sus seccionadores ni sus aisladores")
    void estacion() {
        Station station = new Station();
        Disconnector disconnector = new Disconnector();
        SectionInsulator insulator = new SectionInsulator();
        station.getDisconnectors().add(disconnector);
        station.getSectionInsulators().add(insulator);

        station.deleteWithOwnChildren();

        assertThat(station.isDeleted()).isTrue();
        assertThat(disconnector.isDeleted()).isFalse();
        assertThat(insulator.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("un paquete no se lleva sus vias ni sus estaciones")
    void paquete() {
        ExecutionPackage executionPackage = new ExecutionPackage();
        Track track = new Track();
        Station station = new Station();
        executionPackage.getTracks().add(track);
        executionPackage.getStations().add(station);

        executionPackage.deleteWithOwnChildren();

        assertThat(executionPackage.isDeleted()).isTrue();
        assertThat(track.isDeleted()).isFalse();
        assertThat(station.isDeleted()).isFalse();
    }
}
