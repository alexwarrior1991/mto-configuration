package com.alejandro.mtoconfiguration.core.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Huella del contenido del mensaje en el momento de crearlo.
 * <p>
 * Sirve para correlacionar y para detectar dos eventos de contenido identico. NO es
 * una firma y NO sirve para verificar integridad, por dos motivos:
 * <ul>
 *   <li>Es un SHA-256 sin secreto: quien altere el mensaje puede recalcularlo.</li>
 *   <li>No es verificable en destino. Se calcula sobre el objeto ANTES de
 *       serializarlo, y para comprobarlo habria que deserializar y volver a
 *       serializar. Esa ida y vuelta no es la identidad: un BigDecimal de valor
 *       1.50 vuelve como 1.5 y da otro hash. Con seis campos BigDecimal en
 *       Cantilever y el kp de Profile, no es un caso raro.</li>
 * </ul>
 * Para verificar de verdad, el mensaje viaja firmado en cabecera sobre los bytes
 * reales: ver {@link MessagePayloadSignature}. Aqui existia un {@code isValid()} que
 * prometia esa verificacion y devolvia false para mensajes legitimos; se ha quitado.
 */
@Component
@RequiredArgsConstructor
public class AsynchronousMessageHashService {

    private final ObjectMapper objectMapper;

    public <T> String calculate(AsynchronousMessage<T> message){
        return calculate(
                message.operationId(),
                message.referenceId(),
                message.origin(),
                message.creationDate(),
                message.eventType(),
                message.data()
        );
    }

    private <T> String calculate(
            UUID operationId,
            String referenceId,
            String origin,
            Instant creationDate,
            String eventType,
            T data
    ){
        try {
            String rawValue = objectMapper.writeValueAsString(new HashSource<>(
                    operationId,
                    referenceId,
                    origin,
                    creationDate,
                    eventType,
                    data
            ));

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Error calculating asynchronous message hash", exception);
        }
    }

    private record HashSource<T>(
            UUID operationId,
            String referenceId,
            String origin,
            Instant creationDate,
            String eventType,
            T data
    ) {
    }
}
