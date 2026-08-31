package com.alejandro.mtoconfiguration.masterdata.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El routing key es lo que decide, dentro del exchange topic, a que colas llega el
 * mensaje: README_MESSAGING.md documenta el patron {@code mto.master-data.#} (y
 * {@code mto.master-data.*.deleted} para la cola de borrados), asi que el formato que
 * construye este metodo no es un detalle cosmetico.
 */
class MasterDataRabbitMqNamesTest {

    @Test
    void juntaElPrefijoLaEntidadYLaOperacion() {
        assertThat(MasterDataRabbitMqNames.routingKey("station", MasterDataOperation.CREATED))
                .isEqualTo("mto.master-data.station.created");
    }

    @Test
    void cadaOperacionUsaSuPropioValorDeRouting() {
        assertThat(MasterDataRabbitMqNames.routingKey("station", MasterDataOperation.UPDATED))
                .isEqualTo("mto.master-data.station.updated");

        assertThat(MasterDataRabbitMqNames.routingKey("station", MasterDataOperation.DELETED))
                .isEqualTo("mto.master-data.station.deleted");
    }

    @Test
    void unBorradoSiempreCasaConElPatronDeLaColaDeDeletedDocumentadoEnElReadme() {
        String routingKey = MasterDataRabbitMqNames.routingKey("execution-package", MasterDataOperation.DELETED);

        assertThat(routingKey).matches(MasterDataRabbitMqNames.MASTER_DATA_DELETED_ROUTING_PATTERN
                .replace(".", "\\.")
                .replace("*", "[^.]+"));
    }

    @Test
    void cualquierOperacionCasaConElPatronGeneralDocumentadoEnElReadme() {
        String routingKey = MasterDataRabbitMqNames.routingKey("cantilever", MasterDataOperation.CREATED);

        assertThat(routingKey).startsWith(MasterDataRabbitMqNames.MASTER_DATA_ROUTING_PREFIX + ".");
    }

    @Test
    void elNombreDeLaEntidadSeNormalizaAMinusculasConGuiones() {
        assertThat(MasterDataRabbitMqNames.routingKey("Execution_Package", MasterDataOperation.CREATED))
                .isEqualTo("mto.master-data.execution-package.created");
    }
}
