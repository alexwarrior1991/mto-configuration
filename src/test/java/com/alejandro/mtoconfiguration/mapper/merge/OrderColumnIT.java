package com.alejandro.mtoconfiguration.mapper.merge;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.infraestructure.ProfileMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DEFECTO CONOCIDO: crear un hijo suelto deja su columna de orden a null y rompe la lectura del
 * padre.
 *
 * <p>{@code Track.profiles} y {@code Profile.cantilevers} son listas con {@code @OrderColumn}
 * declaradas en el lado inverso ({@code mappedBy}). Esa columna la mantiene la <b>lista del
 * padre</b>, no la clave ajena del hijo: Hibernate la rellena cuando la coleccion del padre se
 * vuelca. Si el hijo se persiste por su cuenta poniendole solo el padre —que es exactamente lo que
 * hace {@code POST /profiles} con un {@code trackId}, porque el mapeo genera
 * {@code profile.setTrack(referenceMapper.resolve(trackId))} y nunca toca
 * {@code track.getProfiles()}— la fila entra con {@code insertion_order} a null.
 *
 * <p>El fallo no aparece al crear: aparece <b>despues</b>, la primera vez que alguien lee la via,
 * y sale como un 500. Es decir, la peticion que rompe los datos no es la que falla.
 *
 * <p>Estos tests fijan el comportamiento ACTUAL para que quede escrito y para que, el dia que se
 * corrija, fallen y haya que cambiarlos a proposito. Las dos salidas razonables son mantener la
 * coleccion del padre al crear el hijo, o cambiar {@code @OrderColumn} por un {@code @OrderBy}
 * —que no necesita columna y por tanto no se puede quedar a medias—, esto ultimo con su migracion
 * Flyway. La decision no es de un test.
 */
class OrderColumnIT extends AbstractChildMergeIT {

    @Autowired
    private ProfileMapper mapper;

    private Long viaId;

    @BeforeEach
    void seed() {
        ExecutionPackage paquete = new ExecutionPackage();
        paquete.setName("PAQUETE 1");
        paquete.setInitialPackage(false);
        paquete.setLength(1000L);
        paquete.setStartDate(LocalDate.of(2026, 1, 1));
        paquete.setEndDate(LocalDate.of(2026, 12, 31));
        paquete.setEnabled(true);
        em.persist(paquete);

        Track via = new Track();
        via.setName("VIA 1");
        via.setEnabled(true);
        via.setExecutionPackage(paquete);
        em.persist(via);

        flushAndClear();
        viaId = via.getId();
    }

    @Test
    @DisplayName("DEFECTO: un perfil creado suelto deja insertion_order a null")
    void perfilSueltoDejaElOrdenANull() {
        Profile perfil = new Profile();
        perfil.setProfileId("P-001");
        perfil.setKp(new BigDecimal("10.000"));
        perfil.setTrack(em.getReference(Track.class, viaId));   // solo la clave ajena
        em.persist(perfil);
        flushAndClear();

        Object orden = em.createNativeQuery("select insertion_order from profile limit 1")
                .getSingleResult();

        assertThat(orden)
                .as("la columna que mantiene la lista del padre se queda sin rellenar")
                .isNull();
    }

    @Test
    @DisplayName("DEFECTO: y esa fila rompe la siguiente lectura de la via")
    void yEsaFilaRompeLaLecturaDeLaVia() {
        Profile perfil = new Profile();
        perfil.setProfileId("P-001");
        perfil.setKp(new BigDecimal("10.000"));
        perfil.setTrack(em.getReference(Track.class, viaId));
        em.persist(perfil);
        flushAndClear();

        Track releida = em.find(Track.class, viaId);

        // Es el 500 que ve el usuario al abrir una via en la que alguien creo un perfil suelto.
        assertThatThrownBy(() -> releida.getProfiles().size())
                .isInstanceOf(org.hibernate.HibernateException.class)
                .hasMessageContaining("Illegal null value for list index");
    }

    @Test
    @DisplayName("por el adder del padre, en cambio, la fila queda bien")
    void porElAdderLaFilaQuedaBien() {
        // La misma alta hecha como la hace el alta anidada (POST /tracks con profiles dentro),
        // que si pasa por la coleccion del padre.
        Track via = em.find(Track.class, viaId);
        Profile perfil = new Profile();
        perfil.setProfileId("P-001");
        perfil.setKp(new BigDecimal("10.000"));
        via.addProfile(perfil);
        em.persist(perfil);
        flushAndClear();

        Object orden = em.createNativeQuery("select insertion_order from profile limit 1")
                .getSingleResult();

        assertThat(orden).isEqualTo(0);
        assertThat(em.find(Track.class, viaId).getProfiles()).hasSize(1);
    }

    @Test
    @DisplayName("el alta anidada por el mapper tambien deja la fila bien")
    void elAltaAnidadaQuedaBien() {
        // mergeCollection añade el hijo a la coleccion del padre, asi que el orden se rellena.
        ProfileDTO dto = new ProfileDTO();
        dto.setProfileId("P-001");
        dto.setKp("10.000");
        dto.setTrackId(viaId);

        Profile perfil = mapper.toEntity(dto);
        Track via = em.find(Track.class, viaId);
        via.addProfile(perfil);
        em.persist(perfil);
        flushAndClear();

        assertThat(em.find(Track.class, viaId).getProfiles()).hasSize(1);
    }
}
