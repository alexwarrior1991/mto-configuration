package com.alejandro.mtoconfiguration.service.lov.imports;

import com.alejandro.mtoconfiguration.model.synchronous.lov.imports.LovImportReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Carga el catalogo maestro al arrancar, para que un entorno limpio no se quede con las
 * tablas LOV vacias.
 *
 * <p>Es idempotente porque el importador hace upsert por codigo: arrancar diez veces deja
 * el mismo catalogo que arrancar una. Aun asi viene <b>desactivado</b> por defecto, para
 * que nadie se encuentre con escrituras en base de datos que no pidio; se enciende por
 * configuracion en los entornos que interese.
 *
 * <p>Se engancha a {@link ApplicationReadyEvent} y no a un {@code CommandLineRunner} para
 * que corra con el contexto ya completo: el importador usa cache, eventos y auditoria.
 *
 * <p>Si el fichero no esta, avisa y sigue. Un maestro ausente no puede impedir que la
 * aplicacion arranque: el catalogo tambien se puede cargar por el endpoint.
 */
@Component
public class LovMasterSeeder {

    private static final Logger log = LoggerFactory.getLogger(LovMasterSeeder.class);

    private final LovMasterImporter importer;
    private final ResourceLoader resourceLoader;
    private final boolean enabled;
    private final String location;

    public LovMasterSeeder(
            LovMasterImporter importer,
            ResourceLoader resourceLoader,
            @org.springframework.beans.factory.annotation.Value("${app.lov.seed-on-startup:false}")
            boolean enabled,
            @org.springframework.beans.factory.annotation.Value("${app.lov.master-location:file:data/lov-master.xlsx}")
            String location
    ) {
        this.importer = importer;
        this.resourceLoader = resourceLoader;
        this.enabled = enabled;
        this.location = location;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!enabled) {
            log.debug("Siembra del catalogo maestro de LOV desactivada (app.lov.seed-on-startup)");
            return;
        }

        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.warn("No se ha sembrado el catalogo maestro de LOV: no existe {}", location);
            return;
        }

        try (InputStream inputStream = resource.getInputStream()) {
            LovImportReport report = importer.importFrom(inputStream, false);
            log.info("Catalogo maestro de LOV sembrado desde {}: {} altas, {} modificaciones, "
                            + "{} sin cambios, {} errores",
                    location, report.getCreated(), report.getUpdated(),
                    report.getUnchanged(), report.getFailed());
        } catch (Exception e) {
            // Sembrar es una comodidad, no un requisito: si falla, se avisa y la aplicacion
            // sigue arrancando. El catalogo se puede cargar despues por el endpoint.
            log.error("Fallo la siembra del catalogo maestro de LOV desde {}", location, e);
        }
    }
}
