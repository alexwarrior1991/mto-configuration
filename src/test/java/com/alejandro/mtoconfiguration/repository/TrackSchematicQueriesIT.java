package com.alejandro.mtoconfiguration.repository;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulatorSwitch;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.lov.CantileverType;
import com.alejandro.mtoconfiguration.entity.lov.DisconnectorFunction;
import com.alejandro.mtoconfiguration.entity.lov.PoleType;
import com.alejandro.mtoconfiguration.entity.lov.Sectioning;
import com.alejandro.mtoconfiguration.entity.lov.SteadyArmType;
import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.enums.infrastructure.SectionInsulatorInstallationType;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.CantileverRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository.ProfileSectioningCode;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las cinco consultas del esquema de una via ({@code TrackSchematicService}) contra PostgreSQL.
 *
 * <p>Lo que no puede ver el test del servicio con repositorios simulados: que el orden es el
 * fisico ({@code orderInTrack} antes que {@code kp}, como en una via de dos tramos con la
 * kilometracion reiniciada), que un perfil borrado no sale, que lo a-uno viene ya cargado en la
 * misma consulta (sin eso el servicio haria un select por perfil), y que un aislador entra tanto
 * por su via como por la via con la que conecta, pero no uno ajeno.
 */
class TrackSchematicQueriesIT extends AbstractCriteriaSearchIT {

    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private ProfileRepository profileRepository;
    @Autowired
    private CantileverRepository cantileverRepository;
    @Autowired
    private SectionInsulatorRepository sectionInsulatorRepository;

    private Long via1Id;
    private Long via2Id;
    private Long p1Id;
    private Long borradoId;

    @BeforeEach
    void seed() {
        ExecutionPackage paquete = new ExecutionPackage();
        paquete.setName("EP4");
        paquete.setInitialPackage(false);
        paquete.setLength(1000L);
        paquete.setStartDate(LocalDate.of(2026, 1, 1));
        paquete.setEndDate(LocalDate.of(2026, 12, 31));
        paquete.setEnabled(true);
        em.persist(paquete);

        Station atocha = new Station();
        atocha.setName("ATOCHA");
        atocha.setExecutionPackage(paquete);
        em.persist(atocha);

        Track via1 = via("VIA 1", paquete);
        Track via2 = via("VIA 2", paquete);
        via1.addStation(atocha);

        PoleType heb = lov(new PoleType(), "HEB");
        CantileverType pt1 = lov(new CantileverType(), "PT1");
        SteadyArmType sa1 = lov(new SteadyArmType(), "SA1");
        Sectioning s1 = lov(new Sectioning(), "S1");
        DisconnectorFunction feed = lov(new DisconnectorFunction(), "FEED");

        Profile p1 = perfil(via1, "P-001", "10.000", 1);
        p1.setPoleType(heb);
        p1.getSectionings().add(s1);
        Profile p2 = perfil(via1, "P-002", "20.000", 2);
        // Segundo tramo de la misma via, con la kilometracion reiniciada: va DESPUES por orderInTrack.
        Profile p3 = perfil(via1, "P-003", "5.000", 3);
        Profile borrado = perfil(via1, "P-DEL", "30.000", 4);
        Profile ajeno = perfil(via2, "P-101", "10.000", 1);

        Cantilever c1 = mensula(p1, pt1, "-200");
        mensula(p1, null, "200");
        mensula(ajeno, pt1, "0");
        SteadyArm brazo = new SteadyArm();
        brazo.setLength(1200L);
        brazo.setSteadyArmType(sa1);
        c1.addSteadyArm(brazo);
        em.persist(brazo);

        Disconnector seccionador = new Disconnector();
        seccionador.setName("SEC-40");
        seccionador.setOnLoad(true);
        seccionador.setDisconnectorFunction(feed);
        seccionador.setStation(atocha);
        p2.addDisconnector(seccionador);
        em.persist(seccionador);

        SectionInsulator ais1 = aislador("AIS-1", "15.000", via1, via2, atocha);
        SectionInsulatorSwitch w31 = new SectionInsulatorSwitch();
        w31.setCode("W31");
        w31.setKp(new BigDecimal("15.500"));
        w31.setTurnoutDenominator(9);
        w31.setTrack(via1);
        w31.setEnabled(true);
        ais1.addSwitch(w31);
        em.persist(w31);
        aislador("AIS-2", "30.000", via2, via1, atocha);   // conecta con VIA 1: entra en su esquema
        aislador("AIS-3", "40.000", via2, null, atocha);   // ajeno: no entra

        flushAndClear();

        via1Id = via1.getId();
        via2Id = via2.getId();
        p1Id = p1.getId();
        borradoId = borrado.getId();

        Profile aBorrar = em.find(Profile.class, borradoId);
        aBorrar.delete();
        em.merge(aBorrar);
        flushAndClear();
    }

    private Track via(String nombre, ExecutionPackage paquete) {
        Track via = new Track();
        via.setName(nombre);
        via.setEnabled(true);
        paquete.addTrack(via);
        em.persist(via);
        return via;
    }

    private <T extends Lov> T lov(T lov, String code) {
        lov.setCode(code);
        lov.setDescription("Descripcion de " + code);
        lov.setEnabled(true);
        em.persist(lov);
        return lov;
    }

    private Profile perfil(Track via, String codigo, String kp, int orderInTrack) {
        Profile perfil = new Profile();
        perfil.setProfileId(codigo);
        perfil.setKp(new BigDecimal(kp));
        perfil.setOrderInTrack(orderInTrack);
        via.addProfile(perfil);
        em.persist(perfil);
        return perfil;
    }

    private Cantilever mensula(Profile perfil, CantileverType tipo, String stagger) {
        Cantilever mensula = new Cantilever();
        mensula.setCantileverType(tipo);
        mensula.setStagger(new BigDecimal(stagger));
        mensula.setCwHeight(new BigDecimal("5.300"));   // metros: @Digits(1, 3)
        perfil.addCantilever(mensula);
        em.persist(mensula);
        return mensula;
    }

    private SectionInsulator aislador(String nombre, String kp, Track via, Track conectada, Station estacion) {
        SectionInsulator aislador = new SectionInsulator();
        aislador.setName(nombre);
        aislador.setKp(new BigDecimal(kp));
        aislador.setInstallationType(SectionInsulatorInstallationType.TRACK_CONNECTION);
        aislador.setEnabled(true);
        aislador.setTrack(via);
        aislador.setConnectedTrack(conectada);
        aislador.setStation(estacion);
        em.persist(aislador);
        return aislador;
    }

    @Test
    @DisplayName("la cabecera trae la via con su paquete y sus estaciones ya cargados")
    void cabecera() {
        Track via = trackRepository.findForSchematic(via1Id).orElseThrow();

        assertThat(Hibernate.isInitialized(via.getExecutionPackage())).isTrue();
        assertThat(via.getExecutionPackage().getName()).isEqualTo("EP4");
        assertThat(Hibernate.isInitialized(via.getStations())).isTrue();
        assertThat(via.getStations()).extracting(Station::getName).containsExactly("ATOCHA");
    }

    @Test
    @DisplayName("los perfiles salen en el orden fisico, sin el borrado, con lo a-uno ya cargado")
    void perfilesEnOrdenFisico() {
        List<Profile> perfiles = profileRepository.findForSchematic(via1Id);

        assertThat(perfiles).extracting(Profile::getProfileId)
                .as("orderInTrack manda sobre kp: el segundo tramo va detras aunque su KP sea menor")
                .containsExactly("P-001", "P-002", "P-003");

        Profile primero = perfiles.getFirst();
        assertThat(Hibernate.isInitialized(primero.getPoleType())).isTrue();
        assertThat(primero.getPoleType().getCode()).isEqualTo("HEB");
        assertThat(primero.getDisconnector()).isNull();

        Profile segundo = perfiles.get(1);
        assertThat(Hibernate.isInitialized(segundo.getDisconnector())).isTrue();
        assertThat(Hibernate.isInitialized(segundo.getDisconnector().getStation())).isTrue();
        assertThat(segundo.getDisconnector().getStation().getName()).isEqualTo("ATOCHA");
        assertThat(Hibernate.isInitialized(segundo.getDisconnector().getDisconnectorFunction())).isTrue();
        assertThat(segundo.getDisconnector().getDisconnectorFunction().getCode()).isEqualTo("FEED");
    }

    @Test
    @DisplayName("las mensulas de la via vienen con su tipo y su brazo, y solo las de esa via")
    void mensulasDeLaVia() {
        List<Cantilever> mensulas = cantileverRepository.findForSchematic(via1Id);

        assertThat(mensulas).hasSize(2);
        assertThat(mensulas).allSatisfy(mensula -> assertThat(mensula.getProfile().getId()).isEqualTo(p1Id));
        Cantilever primera = mensulas.getFirst();
        assertThat(Hibernate.isInitialized(primera.getCantileverType())).isTrue();
        assertThat(primera.getCantileverType().getCode()).isEqualTo("PT1");
        assertThat(Hibernate.isInitialized(primera.getSteadyArm())).isTrue();
        assertThat(primera.getSteadyArm().getLength()).isEqualTo(1200L);
        assertThat(primera.getSteadyArm().getSteadyArmType().getCode()).isEqualTo("SA1");
        assertThat(mensulas.get(1).getSteadyArm()).isNull();
        assertThat(mensulas.get(1).getCantileverType()).isNull();
    }

    @Test
    @DisplayName("los codigos de seccionamiento llegan como pares perfil-codigo")
    void seccionamientos() {
        List<ProfileSectioningCode> codigos = profileRepository.findSectioningCodesForSchematic(via1Id);

        assertThat(codigos).singleElement().satisfies(par -> {
            assertThat(par.getProfileId()).isEqualTo(p1Id);
            assertThat(par.getCode()).isEqualTo("S1");
        });
    }

    @Test
    @DisplayName("entran los aisladores de la via y los que conectan con ella, por KP, con sus agujas")
    void aisladoresDeLaVia() {
        List<SectionInsulator> aisladores = sectionInsulatorRepository.findForSchematic(via1Id);

        assertThat(aisladores).extracting(SectionInsulator::getName).containsExactly("AIS-1", "AIS-2");
        SectionInsulator primero = aisladores.getFirst();
        assertThat(Hibernate.isInitialized(primero.getStation())).isTrue();
        assertThat(Hibernate.isInitialized(primero.getConnectedTrack())).isTrue();
        assertThat(primero.getConnectedTrack().getName()).isEqualTo("VIA 2");
        assertThat(Hibernate.isInitialized(primero.getSwitches())).isTrue();
        assertThat(primero.getSwitches()).singleElement().satisfies(aguja -> {
            assertThat(aguja.getCode()).isEqualTo("W31");
            assertThat(Hibernate.isInitialized(aguja.getTrack())).isTrue();
            assertThat(aguja.getTrack().getName()).isEqualTo("VIA 1");
        });
        assertThat(aisladores.get(1).getTrack().getName()).isEqualTo("VIA 2");
        assertThat(aisladores.get(1).getConnectedTrack().getName()).isEqualTo("VIA 1");

        assertThat(sectionInsulatorRepository.findForSchematic(via2Id))
                .extracting(SectionInsulator::getName)
                .containsExactly("AIS-1", "AIS-2", "AIS-3");
    }
}
