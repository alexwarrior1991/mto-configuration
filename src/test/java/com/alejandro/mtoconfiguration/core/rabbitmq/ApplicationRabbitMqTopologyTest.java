package com.alejandro.mtoconfiguration.core.rabbitmq;

import com.alejandro.mtoconfiguration.core.messaging.ConfigurationRabbitMqNames;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataRabbitMqNames;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Valida la topologia REAL de application.yaml, no una de laboratorio.
 * <p>
 * Es la que se manda al broker en cada arranque. Un fallo aqui no es un test roto:
 * es un arranque que se cae en el entorno con PRECONDITION_FAILED, arrastrando la
 * declaracion de todo lo demas.
 */
class ApplicationRabbitMqTopologyTest {

    private RabbitMqProperties applicationProperties() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yaml", new ClassPathResource("application.yaml"));

        StandardEnvironment environment = new StandardEnvironment();
        sources.forEach(source -> environment.getPropertySources().addLast(source));

        return Binder.get(environment)
                .bind("app.rabbitmq", RabbitMqProperties.class)
                .orElseThrow(() -> new IllegalStateException("app.rabbitmq no esta en application.yaml"));
    }

    private Declarables declarables(RabbitMqProperties properties) {
        return new RabbitMqConfiguration(properties)
                .rabbitDeclarables(new RabbitMqTopologyValidator(properties));
    }

    private List<Exchange> exchanges(Declarables declarables) {
        return declarables.getDeclarables().stream()
                .filter(Exchange.class::isInstance)
                .map(Exchange.class::cast)
                .toList();
    }

    @Test
    void laTopologiaDeLaAplicacionEsCoherente() throws IOException {
        RabbitMqProperties properties = applicationProperties();

        assertThatCode(() -> new RabbitMqTopologyValidator(properties).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void seDeclaranLosDosExchangesDelContratoComoTopicDurables() throws IOException {
        List<Exchange> exchanges = exchanges(declarables(applicationProperties()));

        // Cambiar esta lista es cambiar el contrato con los servicios que consumen: el de datos
        // maestros lo escuchan los tres, y el propio lo escucha mto-notification.
        assertThat(exchanges)
                .extracting(Exchange::getName)
                .containsExactlyInAnyOrder(
                        MasterDataRabbitMqNames.MASTER_DATA_EXCHANGE,
                        ConfigurationRabbitMqNames.CONFIGURATION_EXCHANGE);

        assertThat(exchanges).allSatisfy(exchange -> {
            assertThat(exchange.getType()).as("%s no es topic", exchange.getName()).isEqualTo("topic");
            assertThat(exchange.isDurable())
                    .as("sin durable, la configuracion de %s no sobrevive a un reinicio del broker",
                            exchange.getName())
                    .isTrue();
            assertThat(exchange.isAutoDelete()).as("%s se autoborra", exchange.getName()).isFalse();
        });
    }

    @Test
    void esteServicioNoDeclaraNingunaColaNiNingunBinding() throws IOException {
        Declarables declarables = declarables(applicationProperties());

        // Este servicio publica y no consume: una cola pertenece a quien la consume, que es quien
        // sabe que limites y que tipo necesita y quien la declara con sus bindings. Las cuatro colas
        // que se declaraban aqui no las leia nadie, y volver a declarar una cola ajena con otros
        // argumentos tumba la declaracion entera con PRECONDITION_FAILED.
        assertThat(declarables.getDeclarables())
                .noneMatch(Queue.class::isInstance)
                .noneMatch(Binding.class::isInstance);

        // Sin colas tampoco hay dead letter que declarar: ni exchanges .dlx ni colas .dlq.
        assertThat(exchanges(declarables))
                .extracting(Exchange::getName)
                .noneMatch(name -> name.endsWith(".dlx"));
    }
}
