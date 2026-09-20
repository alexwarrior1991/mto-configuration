-- El aislador de seccion, con sus agujas, su KP y las dos vias que conecta.
--
-- El plano de seccionamiento dice de un aislador bastante mas de lo que guardaba la tabla
-- (nombre, estacion y estado). Un aislador separa electricamente dos secciones de alimentacion
-- y normalmente se coloca donde CONECTAN DOS VIAS, es decir sobre una aguja; a veces se coloca
-- en medio de una sola via. Cada conexion con la via se identifica por una aguja etiquetada 'W'
-- y un numero, en un punto kilometrico concreto, con la tangente de su desvio escrita al lado:
-- 'W31 1:9', 'W35 1:12', 'W57 1:8'.
--
-- Las agujas van en tabla aparte y no en dos huecos fijos del aislador porque el plano trae los
-- tres casos: dos agujas (lo normal), una sola o ninguna (aislador en medio de una via) y mas de
-- dos cuando coinciden en el mismo KP ('W47,W61').
--
-- La tangente se guarda como DENOMINADOR entero (el 9 de 1:9) y no como texto: el numerador
-- siempre es 1, y asi el valor se puede ordenar y comparar. La representacion '1:N' se compone
-- al salir.
--
-- El KP va en METROS con tres decimales, igual que profile.kilometric_point: el plano escribe
-- '110+176' y eso son 110176.000.
--
-- Todo idempotente (IF NOT EXISTS / DROP IF EXISTS), como el resto de migraciones, por
-- baseline-on-migrate: la migracion tiene que poder pasar sobre una base que ya la tenga.

create sequence if not exists section_insulator_switch_seq start with 1 increment by 1;

-- --------------------------------------------------------------------------------
-- Campos nuevos del aislador.
--
-- Todos anulables: los aisladores que ya estan en base no traen ninguno, y exigirlos aqui
-- convertiria el despliegue en una migracion de datos que nadie puede rellenar todavia.
--
-- La gemela _aud recibe el mismo cambio en ESTA misma migracion: Hibernate construye el mapeo
-- de Envers desde la entidad, asi que una columna anadida sin su gemela hace fallar a
-- ddl-auto: validate y la aplicacion no arranca.
-- --------------------------------------------------------------------------------
alter table section_insulator     add column if not exists kilometric_point numeric(12, 3);
alter table section_insulator_aud add column if not exists kilometric_point numeric(12, 3);

alter table section_insulator     add column if not exists installation_type varchar(30);
alter table section_insulator_aud add column if not exists installation_type varchar(30);

alter table section_insulator     add column if not exists track_id bigint;
alter table section_insulator_aud add column if not exists track_id bigint;

alter table section_insulator     add column if not exists connected_track_id bigint;
alter table section_insulator_aud add column if not exists connected_track_id bigint;

-- Las claves ajenas, solo en la tabla base; la _aud lleva las columnas sin restriccion, por lo
-- mismo que las LOV del perfil (ver V20 y V21).
alter table section_insulator drop constraint if exists fk_section_insulator_track;
alter table section_insulator add constraint fk_section_insulator_track
    foreign key (track_id) references track;

alter table section_insulator drop constraint if exists fk_section_insulator_connected_track;
alter table section_insulator add constraint fk_section_insulator_connected_track
    foreign key (connected_track_id) references track;

create index if not exists idx_section_insulator_track on section_insulator (track_id);

-- --------------------------------------------------------------------------------
-- La aguja.
--
-- Misma forma que el resto de tablas de CRUDEntity: borrado logico (deleted), estado de negocio
-- (status), version optimista y las cuatro columnas de auditoria de quien y cuando.
-- --------------------------------------------------------------------------------
create table if not exists section_insulator_switch (
     deleted boolean not null,
     status boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     id bigint not null,
     kilometric_point numeric(12, 3),
     section_insulator_id bigint not null,
     track_id bigint,
     turnout_denominator integer,
     version_date timestamp(6) not null,
     code varchar(40) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

-- La gemela de auditoria: una fila por revision, sin NOT NULL y sin restricciones hacia el
-- aislador ni hacia la via. La unica clave ajena es rev -> audit_revision.
create table if not exists section_insulator_switch_aud (
     deleted boolean,
     rev integer not null,
     revtype smallint,
     status boolean,
     create_date timestamp(6),
     id bigint not null,
     kilometric_point numeric(12, 3),
     section_insulator_id bigint,
     track_id bigint,
     turnout_denominator integer,
     version_date timestamp(6),
     code varchar(40),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

alter table section_insulator_switch_aud drop constraint if exists fk_section_insulator_switch_aud_rev;
alter table section_insulator_switch_aud add constraint fk_section_insulator_switch_aud_rev
    foreign key (rev) references audit_revision;

alter table section_insulator_switch drop constraint if exists fk_section_insulator_switch_insulator;
alter table section_insulator_switch add constraint fk_section_insulator_switch_insulator
    foreign key (section_insulator_id) references section_insulator;

alter table section_insulator_switch drop constraint if exists fk_section_insulator_switch_track;
alter table section_insulator_switch add constraint fk_section_insulator_switch_track
    foreign key (track_id) references track;

create index if not exists idx_section_insulator_switch_insulator
    on section_insulator_switch (section_insulator_id);
create index if not exists idx_section_insulator_switch_code
    on section_insulator_switch (code);

-- Unico PARCIAL, no unico a secas: CRUDEntity borra en logico, asi que una aguja borrada deja su
-- fila con deleted = true y volver a dar de alta ese mismo codigo en el mismo aislador es
-- legitimo. Sin el 'where', el alta chocaria contra una fila que ya nadie ve.
--
-- El nombre dice 'per_insulator' y no '..._code' a proposito: 'ux_<tabla>_code' es como se llaman
-- los indices que V9 puso en las dieciseis tablas LOV, donde el codigo es unico en toda la tabla.
-- Aqui lo unico es la pareja (aislador, codigo) —W31 existe en muchos aisladores—, y
-- FlywayMigrationIT cuenta los primeros por patron de nombre.
create unique index if not exists ux_section_insulator_switch_per_insulator
    on section_insulator_switch (section_insulator_id, code) where deleted = false;
