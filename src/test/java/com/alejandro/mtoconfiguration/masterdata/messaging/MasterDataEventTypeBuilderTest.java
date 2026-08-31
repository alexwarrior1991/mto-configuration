package com.alejandro.mtoconfiguration.masterdata.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El eventType es lo que un consumidor usa para decidir que handler invocar: su
 * formato es un contrato implicito con quien escucha la cola.
 */
class MasterDataEventTypeBuilderTest {

    @Test
    void juntaElPrefijoLaEntidadEnMayusculasYLaOperacion() {
        assertThat(MasterDataEventTypeBuilder.build("station", MasterDataOperation.CREATED))
                .isEqualTo("MASTER_DATA_STATION_CREATED");
    }

    @Test
    void cadaOperacionProduceUnSufijoDistinto() {
        assertThat(MasterDataEventTypeBuilder.build("station", MasterDataOperation.UPDATED))
                .isEqualTo("MASTER_DATA_STATION_UPDATED");

        assertThat(MasterDataEventTypeBuilder.build("station", MasterDataOperation.DELETED))
                .isEqualTo("MASTER_DATA_STATION_DELETED");
    }

    @Test
    void unNombreDeEntidadConGuionesSeConvierteAGuionesBajos() {
        assertThat(MasterDataEventTypeBuilder.build("execution-package", MasterDataOperation.CREATED))
                .isEqualTo("MASTER_DATA_EXECUTION_PACKAGE_CREATED");
    }

    @Test
    void unNombreDeEntidadConEspaciosSeConvierteAGuionesBajos() {
        assertThat(MasterDataEventTypeBuilder.build("execution package", MasterDataOperation.CREATED))
                .isEqualTo("MASTER_DATA_EXECUTION_PACKAGE_CREATED");
    }

    @Test
    void elNombreDeEntidadViajaEnMayusculasAunqueLlegueEnMinusculas() {
        assertThat(MasterDataEventTypeBuilder.build("cantilever", MasterDataOperation.DELETED))
                .isEqualTo("MASTER_DATA_CANTILEVER_DELETED");
    }
}
