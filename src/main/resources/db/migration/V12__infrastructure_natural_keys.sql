-- Claves naturales unicas para infraestructura, brazo sin longitud y siembra de
-- profile_status. Son los tres requisitos que le faltaban a la importacion masiva.
--
-- 1) IDEMPOTENCIA
-- ---------------
-- No existe NINGUNA restriccion UNIQUE sobre las claves naturales de infraestructura.
-- Con las LOV pasaba lo mismo hasta V9, y el comentario de aquella migracion vale
-- igual aqui: el indice unico es lo que hace que el upsert del importador sea
-- idempotente. Sin el, reimportar el maestro duplicaria paquetes, estaciones, vias y
-- perfiles, y el find-or-create del importador reventaria por resultado no unico.
--
-- El WHERE deleted = false NO es opcional: estas cuatro tablas llevan borrado logico,
-- asi que un indice unico plano chocaria con las filas ya borradas e impediria volver
-- a dar de alta una via que se borro en su dia.
--
-- Se comparan en MAYUSCULAS porque el origen no es consistente: 'HR TRACK 3 HAD' y
-- 'HR Track 3 BIN' conviven en el mismo workbook.
--
-- ANTES DE APLICAR hay que comprobar que no haya duplicados previos. Estas son las
-- cuatro consultas; si alguna devuelve filas, la migracion falla y hay que limpiarlas:
--
--   select upper(name), count(*) from execution_package where deleted = false
--    group by 1 having count(*) > 1;
--   select execution_package_id, upper(name), count(*) from station where deleted = false
--    group by 1,2 having count(*) > 1;
--   select execution_package_id, upper(name), count(*) from track where deleted = false
--    group by 1,2 having count(*) > 1;
--   select track_id, upper(profile_id), count(*) from profile where deleted = false
--    group by 1,2 having count(*) > 1;
--
-- ddl-auto: validate no comprueba indices, asi que si esto quedase a medias la
-- aplicacion arrancaria igual. Lo cubre FlywayMigrationIT.

CREATE UNIQUE INDEX IF NOT EXISTS ux_execution_package_name
    ON execution_package (upper(name)) WHERE deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS ux_station_ep_name
    ON station (execution_package_id, upper(name)) WHERE deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS ux_track_ep_name
    ON track (execution_package_id, upper(name)) WHERE deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS ux_profile_track_profile_id
    ON profile (track_id, upper(profile_id)) WHERE deleted = false;

-- 2) LA LONGITUD DEL BRAZO PASA A OPCIONAL
-- ----------------------------------------
-- De las 14.592 mensulas de los workbooks, 5.691 traen el tipo de brazo pero NO su
-- longitud ('PH', 'PHQ', 'PH-C'...). No es un dato que falte por descuido: no se
-- conoce. Con la columna obligatoria habia que elegir entre tirar el tipo —que si se
-- conoce— o inventarse una longitud; las dos son peores que admitir el nulo.
--
-- El CHECK de rango se queda: en SQL una restriccion se cumple cuando evalua a NULL,
-- asi que sigue acotando 0..2000 para las filas que si traen longitud.
ALTER TABLE steady_arm ALTER COLUMN length DROP NOT NULL;

-- 3) SIEMBRA DE profile_status
-- ----------------------------
-- ProfileValidator exige profileStatus, pero NINGUNA migracion insertaba filas en
-- profile_status y lov-master.xlsx excluye esa entidad a proposito (data/README.md:
-- "ya se cubre con el enum ProfileStatusValue"). El resultado es un fallo silencioso:
-- mandar {"profileStatus":{"code":"DEFINITIVE"}} pasa la validacion, MasterDataService
-- resuelve el codigo a null sin quejarse y el perfil se guarda con profile_status_id
-- nulo. Con 11.715 perfiles a importar, eso son 11.715 filas mal.
--
-- Los tres valores son los de enums/infrastructure/ProfileStatusValue.
--
-- Se insertan con SQL y no por la API, asi que Envers no ve estas altas y
-- profile_status_aud queda sin revision inicial. Es aceptable para un catalogo
-- cerrado de tres filas que nadie va a modificar.
INSERT INTO profile_status (id, code, description, enabled,
                            create_date, create_user, version_date, version_user, version_number)
SELECT nextval('profile_status_seq'), seed.code, seed.description, true,
       now(), 'system', now(), 'system', 1
FROM (VALUES
        ('DRAFT',       'PROFILE_STATUS_DRAFT'),
        ('PROVISIONAL', 'PROFILE_STATUS_PROVISIONAL'),
        ('DEFINITIVE',  'PROFILE_STATUS_DEFINITIVE')
     ) AS seed(code, description)
WHERE NOT EXISTS (
    SELECT 1 FROM profile_status existing WHERE existing.code = seed.code
);
