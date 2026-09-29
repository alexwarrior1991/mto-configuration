package com.alejandro.mtoconfiguration.core.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La clave de enrutado y el {@code eventType} son contrato: {@code mto-notification} bindea su cola
 * con {@code mto.configuration.#} y deriva el tipo de actividad de la clave, asi que el formato que
 * construyen estos metodos no es cosmetico.
 */
class ConfigurationRabbitMqNamesTest {

    @Test
    @DisplayName("la clave de enrutado es mto.configuration.<entidad>.<evento>")
    void laClaveDeEnrutado() {
        assertThat(ConfigurationRabbitMqNames.routingKey("job", "finished"))
                .isEqualTo("mto.configuration.job.finished")
                .startsWith(ConfigurationRabbitMqNames.CONFIGURATION_ROUTING_PREFIX + ".");
    }

    @Test
    @DisplayName("el eventType es CONFIGURATION_<ENTIDAD>_<EVENTO>, como MASTER_DATA_<ENTIDAD>_<OPERACION>")
    void elEventType() {
        assertThat(ConfigurationRabbitMqNames.eventType("job", "finished"))
                .isEqualTo("CONFIGURATION_JOB_FINISHED");
    }

    @Test
    @DisplayName("los nombres se normalizan igual que los de datos maestros: minusculas y guiones en la clave, mayusculas y barras bajas en el tipo")
    void losNombresSeNormalizan() {
        assertThat(ConfigurationRabbitMqNames.routingKey("Section Insulator", "Item_Failed"))
                .isEqualTo("mto.configuration.section-insulator.item-failed");
        assertThat(ConfigurationRabbitMqNames.eventType("Section Insulator", "Item_Failed"))
                .isEqualTo("CONFIGURATION_SECTION_INSULATOR_ITEM_FAILED");
    }

    @Test
    @DisplayName("el exchange propio no es el de datos maestros: quien escucha mto.master-data.# no recibe esto")
    void elExchangeEsElPropio() {
        assertThat(ConfigurationRabbitMqNames.CONFIGURATION_EXCHANGE).isEqualTo("mto.configuration.exchange");
        assertThat(ConfigurationRabbitMqNames.CONFIGURATION_ROUTING_PATTERN).isEqualTo("mto.configuration.#");
        assertThat(ConfigurationRabbitMqNames.routingKey("job", "finished")).doesNotStartWith("mto.master-data");
    }
}
