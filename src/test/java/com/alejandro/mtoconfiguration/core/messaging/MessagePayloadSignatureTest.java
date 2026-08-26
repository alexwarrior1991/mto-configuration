package com.alejandro.mtoconfiguration.core.messaging;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La firma se calcula sobre los BYTES que viajan, y eso es lo que la hace verificable.
 * <p>
 * El messageHash que va dentro del payload se calcula sobre el objeto antes de
 * serializarlo, asi que comprobarlo obliga a deserializar y volver a serializar. Esa
 * ida y vuelta no es la identidad: un BigDecimal de 1.50 vuelve como 1.5. Estos tests
 * fijan que la firma no tiene ese problema.
 */
class MessagePayloadSignatureTest {

    private MessagePayloadSignature conSecreto(String secreto) {
        MessageSignatureProperties properties = new MessageSignatureProperties();
        properties.setSecret(secreto);
        return new MessagePayloadSignature(properties);
    }

    private MessagePayloadSignature sinSecreto() {
        return new MessagePayloadSignature(new MessageSignatureProperties());
    }

    private static final String PAYLOAD =
            "{\"data\":{\"values\":{\"cwHeight\":1.50,\"stagger\":2.00}}}";

    @Test
    void unPayloadConDecimalesSeVerificaSinTocarUnSoloByte() {
        // Es justo el caso que el messageHash del payload no puede validar: 1.50 y 2.00
        // vuelven de la deserializacion como 1.5 y 2, y el hash deja de cuadrar.
        MessagePayloadSignature firma = conSecreto("secreto-compartido");

        byte[] recibido = PAYLOAD.getBytes(StandardCharsets.UTF_8);

        assertThat(firma.verify(recibido, firma.sign(PAYLOAD)))
                .as("firmando bytes no hay nada que reserializar, asi que los decimales dan igual")
                .isTrue();
    }

    @Test
    void cambiarUnSoloCaracterInvalidaLaFirma() {
        MessagePayloadSignature firma = conSecreto("secreto-compartido");
        String firmado = firma.sign(PAYLOAD);

        String manipulado = PAYLOAD.replace("1.50", "9.99");

        assertThat(firma.verify(manipulado.getBytes(StandardCharsets.UTF_8), firmado)).isFalse();
    }

    @Test
    void conSecretoDistintoLaFirmaNoCuadra() {
        // Esto es lo que aporta el HMAC frente a un hash pelado: sin el secreto no se
        // puede producir una firma valida, por mucho que se conozca el algoritmo.
        String firmadoPorElEmisor = conSecreto("secreto-bueno").sign(PAYLOAD);

        assertThat(conSecreto("secreto-robado")
                .verify(PAYLOAD.getBytes(StandardCharsets.UTF_8), firmadoPorElEmisor))
                .isFalse();
    }

    @Test
    void elAlgoritmoViajaParaQueElConsumidorNoLoAdivine() {
        assertThat(conSecreto("secreto").algorithm()).isEqualTo("HMAC-SHA256");
        assertThat(sinSecreto().algorithm()).isEqualTo("SHA-256");
    }

    @Test
    void sinSecretoSigueFirmandoPeroSoloDetectaCorrupcion() {
        MessagePayloadSignature firma = sinSecreto();

        assertThat(firma.verify(PAYLOAD.getBytes(StandardCharsets.UTF_8), firma.sign(PAYLOAD))).isTrue();
        assertThat(firma.verify("otra cosa".getBytes(StandardCharsets.UTF_8), firma.sign(PAYLOAD))).isFalse();
    }

    @Test
    void unSecretoEnBlancoCuentaComoNoTenerlo() {
        assertThat(conSecreto("   ").algorithm()).isEqualTo("SHA-256");
        assertThat(conSecreto("").algorithm()).isEqualTo("SHA-256");
    }

    @Test
    void laFirmaEsEstable() {
        MessagePayloadSignature firma = conSecreto("secreto");

        assertThat(firma.sign(PAYLOAD)).isEqualTo(firma.sign(PAYLOAD));
        assertThat(firma.sign(PAYLOAD)).matches("[0-9a-f]{64}");
    }

    @Test
    void unaFirmaAusenteNoSeDaPorBuena() {
        MessagePayloadSignature firma = conSecreto("secreto");

        assertThat(firma.verify(PAYLOAD.getBytes(StandardCharsets.UTF_8), null)).isFalse();
        assertThat(firma.verify(null, "loquesea")).isFalse();
        assertThatThrownBy(() -> firma.sign((byte[]) null)).isInstanceOf(IllegalArgumentException.class);
    }
}
