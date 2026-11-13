-- La clave natural del aislador de seccion: su nombre dentro de su estacion.
--
-- Es la clave con la que el importador del maestro reconoce el aislador de una fila
-- (SectionInsulatorRepository.findByNameIgnoreCaseAndStationId), pero ninguna restriccion la
-- guardaba: V12 puso el indice unico a los paquetes, las estaciones, las vias y los perfiles, y V28
-- a los puntos de acceso; el aislador (y el seccionador) se quedaron sin el suyo. La API aceptaba
-- un segundo aislador con el mismo nombre en la misma estacion, y a partir de ahi el importador no
-- podia con esa fila: su busqueda por clave natural encontraba dos.
--
-- Como en V12 y V28: en MAYUSCULAS, porque el origen no es consistente, y con WHERE deleted = false,
-- porque el borrado es logico y un aislador borrado no puede impedir volver a darlo de alta. Un
-- nombre repetido sale por la API como un 409 BUS-002, igual que en los demas maestros.
--
-- 1) ANTES DE CREAR EL INDICE, SE MIRA SI CABE
-- --------------------------------------------
-- Ninguna migracion siembra aisladores, y el maestro (data/profile-master.xlsx) no trae ninguno
-- repetido: el importador no puede crear dos, porque busca antes por esta misma clave. Pero la API
-- si podia, asi que una base desplegada puede tener repetidos dados de alta a mano. Con ellos el
-- CREATE UNIQUE INDEX fallaria con el mensaje del motor, que nombra una sola pareja; aqui se dicen
-- todos, con sus ids, y que hacer. La migracion es transaccional: si falla no deja nada a medias, y
-- la aplicacion no arranca hasta que se arregle el dato (renombrar uno, o borrarlo en logico) y se
-- vuelva a arrancar.
--
--   select station_id, upper(name), count(*), string_agg(id::text, ',' order by id)
--     from section_insulator where deleted = false
--    group by 1, 2 having count(*) > 1;
DO $$
DECLARE
    repetidos text;
BEGIN
    SELECT string_agg(format('estacion %s, nombre %s (ids %s)', station_id, nombre, ids), '; ')
      INTO repetidos
      FROM (SELECT station_id,
                   upper(name) AS nombre,
                   string_agg(id::text, ', ' ORDER BY id) AS ids
              FROM section_insulator
             WHERE deleted = false
               AND station_id IS NOT NULL
             GROUP BY station_id, upper(name)
            HAVING count(*) > 1
             ORDER BY station_id, upper(name)
             LIMIT 50) AS d;

    IF repetidos IS NOT NULL THEN
        RAISE EXCEPTION 'V31 no se puede aplicar: hay aisladores de seccion vivos con el mismo nombre en la misma estacion: %', repetidos
            USING HINT = 'Renombra o borra en logico (deleted = true) los que sobran, dejando uno por estacion y nombre, y vuelve a arrancar. La migracion no ha cambiado nada.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_section_insulator_station_name
    ON section_insulator (station_id, upper(name)) WHERE deleted = false;

-- 2) EL SECCIONADOR NO TIENE INDICE, PERO SE AVISA DE LOS QUE YA ESTAN REPETIDOS
-- -------------------------------------------------------------------------------
-- Su clave es el nombre dentro de su PAQUETE, y el paquete no es una columna suya: sale de su
-- estacion, de la via de su poste o de su via propia, lo que tenga (la estacion es opcional). No
-- cabe en un indice sin guardar el paquete en la fila, asi que la comprueba la aplicacion
-- (DisconnectorNaturalKeyGuard, 409 BUS-002). Aqui solo se AVISA, sin fallar, de los que una base
-- desplegada ya tenga repetidos: el importador no carga su fila y la API no deja modificarlos hasta
-- que se renombre o se borre uno. Flyway lo escribe en el log del arranque.
DO $$
DECLARE
    repetidos text;
BEGIN
    SELECT string_agg(format('paquete %s, nombre %s (ids %s)', paquete, nombre, ids), '; ')
      INTO repetidos
      FROM (SELECT paquete, nombre, string_agg(id::text, ', ' ORDER BY id) AS ids
              FROM (SELECT d.id, upper(d.name) AS nombre, s.execution_package_id AS paquete
                      FROM disconnector d
                      JOIN station s ON s.id = d.station_id AND s.deleted = false
                     WHERE d.deleted = false
                    UNION
                    SELECT d.id, upper(d.name), t.execution_package_id
                      FROM disconnector d
                      JOIN profile p ON p.id = d.profile_id AND p.deleted = false
                      JOIN track t ON t.id = p.track_id AND t.deleted = false
                     WHERE d.deleted = false
                    UNION
                    SELECT d.id, upper(d.name), t.execution_package_id
                      FROM disconnector d
                      JOIN track t ON t.id = d.track_id AND t.deleted = false
                     WHERE d.deleted = false) AS en_paquete
             WHERE paquete IS NOT NULL
             GROUP BY paquete, nombre
            HAVING count(*) > 1
             ORDER BY paquete, nombre
             LIMIT 50) AS d;

    IF repetidos IS NOT NULL THEN
        RAISE WARNING 'V31: hay seccionadores vivos con el mismo nombre en el mismo paquete: %. El importador del maestro no carga esas filas y la API no deja modificarlos (409 BUS-002) hasta que se renombre o se borre uno.', repetidos;
    END IF;
END $$;
