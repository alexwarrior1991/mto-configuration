package com.alejandro.mtoconfiguration.core.messaging;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Firma los BYTES que de verdad viajan en el mensaje.
 * <p>
 * Esto es lo que distingue una firma verificable de una que no lo es. El
 * {@code messageHash} que va dentro del payload se calcula sobre el objeto ANTES de
 * serializarlo, asi que para comprobarlo hay que deserializar y volver a serializar,
 * y esa ida y vuelta no es la identidad: un {@code BigDecimal} de valor {@code 1.50}
 * se escribe {@code 1.50}, vuelve como {@code 1.5} y produce un hash distinto. Con
 * seis campos BigDecimal en Cantilever y el kp de Profile, eso no es un caso raro.
 * <p>
 * Firmando los bytes recibidos no hay nada que reserializar: el consumidor calcula
 * sobre exactamente lo mismo que calculo el emisor. Por eso la firma viaja en una
 * CABECERA y no dentro del payload, que no puede contener su propia firma.
 * <p>
 * Con secreto configurado es HMAC-SHA256 y protege de manipulacion. Sin secreto es un
 * SHA-256 simple: detecta corrupcion, pero no manipulacion, porque quien altere el
 * mensaje puede recalcularlo. El algoritmo usado viaja en su propia cabecera para que
 * el consumidor no tenga que adivinarlo.
 */
@Slf4j
public class MessagePayloadSignature {

    public static final String HEADER_SIGNATURE = "messageSignature";
    public static final String HEADER_SIGNATURE_ALGORITHM = "messageSignatureAlgorithm";

    static final String HMAC_ALGORITHM = "HmacSHA256";
    static final String DIGEST_ALGORITHM = "SHA-256";

    private final MessageSignatureProperties properties;

    public MessagePayloadSignature(MessageSignatureProperties properties) {
        this.properties = properties;

        if (!properties.hasSecret()) {
            log.info("Mensajeria sin secreto de firma: los mensajes se firman con {} simple, "
                    + "que detecta corrupcion pero no manipulacion. Configura "
                    + "app.messaging.signature.secret para usar HMAC.", DIGEST_ALGORITHM);
        }
    }

    /** Nombre del algoritmo, tal y como viaja en la cabecera. */
    public String algorithm() {
        return properties.hasSecret() ? "HMAC-SHA256" : DIGEST_ALGORITHM;
    }

    public String sign(byte[] payload) {
        if (payload == null) {
            throw new IllegalArgumentException("El payload a firmar no puede ser nulo");
        }

        return properties.hasSecret() ? hmac(payload) : digest(payload);
    }

    public String sign(String payload) {
        return sign(payload.getBytes(StandardCharsets.UTF_8));
    }

    /** Comparacion en tiempo constante: comparar firmas con equals filtra informacion. */
    public boolean verify(byte[] payload, String signature) {
        if (payload == null || signature == null) {
            return false;
        }

        return MessageDigest.isEqual(
                sign(payload).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("Error firmando el mensaje", exception);
        }
    }

    private String digest(byte[] payload) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance(DIGEST_ALGORITHM).digest(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("Error calculando el hash del mensaje", exception);
        }
    }
}
