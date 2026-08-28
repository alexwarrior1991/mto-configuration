package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.core.exception.GenericException;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Trabajo de carga masiva: crea o modifica perfiles uno a uno, con progreso parcial.
 *
 * <h2>Por que uno a uno y no {@code bulkCreate}</h2>
 *
 * <p>{@code ProfileService.bulkCreate} y {@code bulkUpdate} son <b>una sola transaccion para todo
 * el lote</b>, y eso es exactamente lo contrario de lo que necesita un trabajo de fondo. Con esa
 * semantica no hay progreso que enseñar —nada esta confirmado hasta el final— y un unico elemento
 * invalido en la posicion 9.999 deshace los 9.998 anteriores. Para una peticion sincrona de veinte
 * elementos, todo o nada es la garantia correcta; para una carga de miles lanzada en segundo plano,
 * es la forma de tirar horas de trabajo por un dato mal escrito.</p>
 *
 * <p>Por eso se llama a {@code create}/{@code update} elemento a elemento: cada uno abre <b>su
 * propia</b> transaccion —son metodos {@code @Transactional} de otro bean, asi que el proxy las
 * separa de verdad— y con ellos vienen intactas las validaciones, los mappers, la auditoria, los
 * eventos del outbox y la invalidacion de cache. Reimplementar el bucle a mano contra el
 * repositorio habria sido mas rapido y se habria saltado todo eso en silencio.</p>
 *
 * <p>El precio es real y conviene decirlo: N transacciones pequenas en vez de una grande, es decir,
 * mas commits y un evento de outbox por elemento. Se paga a cambio de progreso visible y de que un
 * fallo cueste un elemento y no la carga entera; si algun dia pesa demasiado, el paso siguiente es
 * un metodo transaccional por bloques (cien elementos por transaccion), que conserva el progreso
 * parcial con una fraccion de los commits. Lo que no se debe hacer es meter los miles de elementos
 * en una unica transaccion.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileBulkJobRunner {

    private static final String OPERATION_CREATE = "create";
    private static final String OPERATION_UPDATE = "update";

    private final ProfileService profileService;

    public void run(UUID jobId, JobType type, List<ProfileDTO> dtoList, ProfileJobProgress progress) {
        boolean update = type == JobType.PROFILE_BULK_UPDATE;
        String operation = update ? OPERATION_UPDATE : OPERATION_CREATE;

        log.info("Carga masiva en curso jobId={} type={} elementos={}", jobId, type, dtoList.size());

        for (int index = 0; index < dtoList.size(); index++) {
            ProfileDTO dto = dtoList.get(index);

            try {
                if (update) {
                    updateItem(dto);
                } else {
                    profileService.create(dto);
                }

                progress.itemSucceeded();
            } catch (Exception e) {
                // Un elemento fallido no tumba el trabajo: se anota y se sigue. Solo un error
                // global —que se propaga desde fuera de este bucle— da el trabajo por FAILED.
                log.warn("Elemento {} fallido en el trabajo jobId={}: {}", index, jobId, e.toString());
                progress.itemFailed(index, operation, codeOf(e), messageOf(e));
            }

            // Una interrupcion durante el apagado no debe dejar el bucle corriendo contra una
            // base de datos que se esta cerrando. Se sale y el trabajo termina como fallido.
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException(
                        "Carga masiva interrumpida tras %d elementos".formatted(progress.getProcessedItems()));
            }
        }
    }

    /**
     * En una modificacion el identificador es obligatorio, y se comprueba aqui.
     *
     * <p>Sin esta comprobacion, un DTO sin id no fallaria: {@code update} lo descarta por el filtro
     * {@code Utils::exists} y devuelve sin hacer nada, de modo que el elemento se contaria como
     * exitoso y el cliente creeria haber modificado algo que nunca se toco. Es peor que un error.</p>
     */
    private void updateItem(ProfileDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new IllegalArgumentException(
                    "El identificador es obligatorio para modificar un perfil");
        }

        profileService.update(dto);
    }

    /** Codigo de error cuando la excepcion lo trae; si no, el nombre de la clase, que ya orienta. */
    private String codeOf(Exception e) {
        if (e instanceof GenericException generic && generic.getCode() != null) {
            return generic.getCode();
        }

        return e.getClass().getSimpleName();
    }

    /**
     * Mensaje legible del fallo.
     *
     * <p>Las excepciones de negocio del proyecto llevan sus alertas por campo, que es justo lo que
     * necesita quien tiene que corregir la fila: se juntan en una linea en lugar de quedarse con el
     * {@code getMessage()}, que solo trae la primera.</p>
     */
    private String messageOf(Exception e) {
        if (e instanceof BaseException baseException && !baseException.getErrors().isEmpty()) {
            return baseException.getErrors().stream()
                    .map(this::describe)
                    .collect(Collectors.joining("; "));
        }

        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private String describe(Alert alert) {
        List<String> fields = alert.getFields();

        return fields.isEmpty()
                ? alert.getMessage()
                : "%s %s".formatted(alert.getMessage(), fields);
    }
}
