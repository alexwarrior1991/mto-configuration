-- El punto de acceso a la via: por donde un vehiculo bivial sale de la carretera y se encarrila.
--
-- Un maestro nuevo, con el patron de los demas de infraestructura y su evento propio
-- (track-access-point) por mto.master-data.exchange. Lo materializa mto-resources, que aplica con el
-- las reglas de la flota al registrar donde se encarrila un vehiculo: si el acceso esta habilitado,
-- si admite biviales, si esta en la via de la ubicacion, si es un acceso preparado y si aguanta el
-- peso del modelo.
--
-- Cuelga de su VIA, que es obligatoria, y opcionalmente de una estacion: muchos accesos estan en
-- plena via. Ni la via ni la estacion lo llevan en una coleccion, asi que no se escribe anidado.
--
--   - kilometric_point, en METROS con tres decimales, como el del aislador (V23). Anulable, tambien
--     como el del aislador;
--   - kind (PREPARED_ACCESS, LEVEL_CROSSING, DEPOT) y side (LEFT, RIGHT, BOTH), texto con el nombre
--     del enum, como installation_type (V23) y drive_type (V25). Obligatorios: es una tabla nueva y
--     no hay filas viejas que no los traigan;
--   - road_rail_access, si sirve para encarrilar biviales. Obligatorio y TRUE por defecto;
--   - max_axle_load_t y max_gross_weight_t, en toneladas, opcionales y no negativos;
--   - latitude y longitude, en grados decimales (WGS 84), opcionales y SIEMPRE juntas;
--   - status, el enabled de la API, como en track y section_insulator.
--
-- Las reglas de valores las comprueba TrackAccessPointValidator, que las devuelve como 400 con su
-- campo; los CHECK de aqui son la red por debajo, para lo que escriba la base sin pasar por la API.
--
-- La gemela _aud nace en ESTA misma migracion: Hibernate construye el mapeo de Envers desde la
-- entidad, y sin ella ddl-auto: validate no arranca. Sin NOT NULL, sin CHECK y sin claves ajenas
-- salvo rev -> audit_revision, como section_insulator_switch_aud (V23).
--
-- Todo idempotente (IF NOT EXISTS / DROP IF EXISTS), como el resto de migraciones, por
-- baseline-on-migrate.

create sequence if not exists track_access_point_seq start with 1 increment by 1;

create table if not exists track_access_point (
     deleted boolean not null,
     status boolean not null,
     road_rail_access boolean not null default true,
     version_number integer not null,
     create_date timestamp(6) not null,
     id bigint not null,
     track_id bigint not null,
     station_id bigint,
     kilometric_point numeric(12, 3),
     max_axle_load_t numeric(8, 3),
     max_gross_weight_t numeric(8, 3),
     latitude numeric(9, 6),
     longitude numeric(9, 6),
     version_date timestamp(6) not null,
     kind varchar(30) not null,
     side varchar(10) not null,
     name varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id),
     constraint ck_track_access_point_kp check (kilometric_point >= 0),
     constraint ck_track_access_point_max_axle_load check (max_axle_load_t >= 0),
     constraint ck_track_access_point_max_gross_weight check (max_gross_weight_t >= 0),
     constraint ck_track_access_point_latitude check (latitude between -90 and 90),
     constraint ck_track_access_point_longitude check (longitude between -180 and 180),
     constraint ck_track_access_point_coordinates check ((latitude is null) = (longitude is null))
);

create table if not exists track_access_point_aud (
     deleted boolean,
     rev integer not null,
     revtype smallint,
     status boolean,
     road_rail_access boolean,
     create_date timestamp(6),
     id bigint not null,
     track_id bigint,
     station_id bigint,
     kilometric_point numeric(12, 3),
     max_axle_load_t numeric(8, 3),
     max_gross_weight_t numeric(8, 3),
     latitude numeric(9, 6),
     longitude numeric(9, 6),
     version_date timestamp(6),
     kind varchar(30),
     side varchar(10),
     name varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

alter table track_access_point_aud drop constraint if exists fk_track_access_point_aud_rev;
alter table track_access_point_aud add constraint fk_track_access_point_aud_rev
    foreign key (rev) references audit_revision;

alter table track_access_point drop constraint if exists fk_track_access_point_track;
alter table track_access_point add constraint fk_track_access_point_track
    foreign key (track_id) references track;

alter table track_access_point drop constraint if exists fk_track_access_point_station;
alter table track_access_point add constraint fk_track_access_point_station
    foreign key (station_id) references station;

-- La lista de los accesos de una via es la consulta que mas se hace (y el republicado por via).
create index if not exists idx_track_access_point_track on track_access_point (track_id);
create index if not exists idx_track_access_point_station on track_access_point (station_id);

-- La clave natural: el nombre dentro de su via, en mayusculas y sin contar lo borrado, como las de
-- V12. Dos accesos con el mismo nombre en la misma via serian el mismo dado de alta dos veces (409
-- BUS-002); borrar uno no impide volver a darlo de alta.
create unique index if not exists ux_track_access_point_track_name
    on track_access_point (track_id, upper(name)) where deleted = false;
