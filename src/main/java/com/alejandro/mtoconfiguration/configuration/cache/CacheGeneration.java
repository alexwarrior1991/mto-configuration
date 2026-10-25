package com.alejandro.mtoconfiguration.configuration.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * La generacion de la cache: un contador en Redis ({@code <aplicacion>::cache-generation}) que sube
 * cada vez que una escritura la vacia. Cierra la carrera de una cache que se rellena al leer.
 *
 * <p><b>La carrera.</b> Un {@code @Cacheable} que falla en la cache lee la base y guarda lo leido
 * despues. Si entre las dos cosas una escritura confirma y vacia la cache ({@code AFTER_COMMIT},
 * {@link RedisCacheEvictService}), el {@code put} llega despues del {@code DEL} y deja en Redis lo de
 * antes de la escritura hasta el TTL: 24 horas un catalogo. Asi una lista pedida al abrir una
 * pantalla, lenta en frio, tapo un alta hecha mientras tanto (el e2e de mto-frontend).
 *
 * <p><b>Como se cierra.</b> El vaciado sube la generacion antes de borrar ({@link #advance}), por la
 * misma conexion. {@link ResilientCache} apunta la generacion cuando una lectura falla y, despues de
 * guardar lo que cargo, la vuelve a leer: si cambio, una escritura confirmo mientras se cargaba y lo
 * guardado puede ser de antes, asi que lo borra. Cualquier orden cae de un lado: si la generacion
 * sube antes de esa segunda lectura, el {@code put} se deshace; si sube despues, el {@code DEL} de
 * ese vaciado llega despues del {@code put} y lo borra el. Lo que queda es el instante entre el
 * {@code put} y su deshacer, en el que otra lectura podria llevarse el valor viejo una vez sin
 * volver a guardarlo.
 *
 * <p>Una sola generacion para toda la cache, a proposito: el vaciado va por patrones que cruzan
 * servicios y caches, y casi toda escritura vacia ademas el esquema de via. Con una por servicio
 * habria que saber que patrones vacian cada clave. El coste de la global es que una escritura
 * deshace los {@code put} de cualquier lectura que estuviera cargando a la vez, y esa lectura se
 * guarda en la siguiente. Durante una importacion, que escribe sin parar, la cache apenas guarda,
 * pero cada escritura la vaciaba igual.
 *
 * <p>La clave no tiene TTL y no la toca ningun patron del vaciado ({@code <aplicacion>::<cache>::...}).
 * Lo fija {@code CacheGenerationIT}, contra Redis de verdad.
 */
@Component
public class CacheGeneration {

    private final RedisConnectionFactory connectionFactory;
    private final byte[] key;

    public CacheGeneration(
            RedisConnectionFactory connectionFactory,
            @Value("${cache.application:mto-configuration}") String applicationName) {
        this.connectionFactory = connectionFactory;
        this.key = (applicationName + "::cache-generation").getBytes(StandardCharsets.UTF_8);
    }

    /** La generacion actual: 0 mientras nadie haya vaciado nada. Un fallo de Redis se propaga. */
    public long current() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            byte[] value = connection.stringCommands().get(key);
            return value == null ? 0 : Long.parseLong(new String(value, StandardCharsets.UTF_8));
        }
    }

    /** Sube la generacion por la conexion del vaciado, que lo hace antes de borrar. */
    public void advance(RedisConnection connection) {
        connection.stringCommands().incr(key);
    }
}
