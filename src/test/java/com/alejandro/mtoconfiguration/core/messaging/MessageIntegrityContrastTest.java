package com.alejandro.mtoconfiguration.core.messaging;

import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataChangedEvent;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataOperation;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deja documentado, con codigo que se ejecuta, por que la firma va sobre los bytes.
 * <p>
 * Es el contraste entre las dos formas de comprobar un mensaje. Una sobrevive a la
 * ida y vuelta por la red y la otra no, y la diferencia no es teorica: los payloads
 * de Cantilever llevan seis BigDecimal y los de Profile llevan el kp.
 */
class MessageIntegrityContrastTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AsynchronousMessageHashService hashService =
            new AsynchronousMessageHashService(objectMapper);

    private AsynchronousMessage<MasterDataChangedEvent> mensajeConDecimales() throws Exception {
        AsynchronousMessageFactory factory = new AsynchronousMessageFactory(hashService);
        java.lang.reflect.Field applicationName =
                AsynchronousMessageFactory.class.getDeclaredField("applicationName");
        applicationName.setAccessible(true);
        applicationName.set(factory, "mto-configuration");

        Map<String, Object> values = new LinkedHashMap<>();
        // 1.50 y no 1.5: los decimales del dominio vienen de la base de datos con su
        // escala, y ahi esta el problema.
        values.put("cwHeight", new BigDecimal("1.50"));
        values.put("stagger", new BigDecimal("2.00"));

        return factory.create("cantilever-1", "MASTER_DATA_CANTILEVER_UPDATED",
                new MasterDataChangedEvent("cantilever", "1", MasterDataOperation.UPDATED, values));
    }

    @Test
    void elHashDelPayloadNoSobreviveALaIdaYVuelta() throws Exception {
        AsynchronousMessage<MasterDataChangedEvent> original = mensajeConDecimales();

        String json = objectMapper.writeValueAsString(original);
        AsynchronousMessage<MasterDataChangedEvent> recibido = objectMapper.readValue(
                json, new TypeReference<AsynchronousMessage<MasterDataChangedEvent>>() {});

        // 1.50 se escribe 1.50 y vuelve como 1.5: al recalcular sale otro hash.
        assertThat(hashService.calculate(recibido))
                .as("""
                        El messageHash del payload se calcula sobre el OBJETO, asi que \
                        comprobarlo obliga a reserializar, y esa ida y vuelta no es la \
                        identidad. Por eso no existe un isValid(): devolvia false para \
                        mensajes perfectamente legitimos.""")
                .isNotEqualTo(original.messageHash());
    }

    @Test
    void laFirmaSobreLosBytesSiSobrevive() throws Exception {
        MessageSignatureProperties properties = new MessageSignatureProperties();
        properties.setSecret("secreto-compartido");
        MessagePayloadSignature firma = new MessagePayloadSignature(properties);

        // Lo que hace el emisor: serializa una vez y firma ESOS bytes.
        byte[] enviado = objectMapper.writeValueAsString(mensajeConDecimales())
                .getBytes(StandardCharsets.UTF_8);
        String firmado = firma.sign(enviado);

        // Lo que hace el consumidor: firma los bytes recibidos, sin deserializar nada.
        byte[] recibido = enviado.clone();

        assertThat(firma.verify(recibido, firmado))
                .as("mismos bytes, misma firma: no hay reserializacion que pueda estropearla")
                .isTrue();
    }
}
