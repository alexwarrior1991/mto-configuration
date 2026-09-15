-- El perfil guarda su configuracion de montaje: catalogo nuevo, assembly_configuration.
--
-- Llega con el sinoptico de la Linha Rubi (Metro do Porto, EP RUBI), que escribe en cada
-- apoyo la configuracion de montaje de su catenaria: 'C.F.21' para la catenaria flexible,
-- 'C.C.2' para la de mensula (33 configuraciones distintas en 150 apoyos). Los once
-- workbooks ferroviarios no traen esa columna, asi que en sus perfiles queda a NULL.
--
-- Es una LOV mas del perfil, con la misma forma que support_type (V1 + V9 + V20): tabla
-- base con codigo unico, gemela _aud para Envers y una clave ajena opcional desde profile.
-- @ManyToOne y no N:M porque el origen escribe una sola configuracion por apoyo.
--
-- Todo idempotente (IF NOT EXISTS / DROP IF EXISTS), como el resto de migraciones, por
-- baseline-on-migrate: la migracion tiene que poder pasar sobre una base que ya la tenga.

create sequence if not exists assembly_configuration_seq start with 1 increment by 1;

create table if not exists assembly_configuration (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     drawing_number bigint,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(40) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

-- La gemela de auditoria: una fila por revision, sin NOT NULL y sin restricciones hacia
-- el catalogo, como todas las _aud. La unica clave ajena es rev -> audit_revision.
create table if not exists assembly_configuration_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     drawing_number bigint,
     id bigint not null,
     version_date timestamp(6),
     code varchar(40),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

alter table assembly_configuration_aud drop constraint if exists fk_assembly_configuration_aud_rev;
alter table assembly_configuration_aud add constraint fk_assembly_configuration_aud_rev
    foreign key (rev) references audit_revision;

-- El mismo indice (code, description) que declara la entidad y el mismo indice unico por
-- codigo que V9 puso en las otras dieciseis tablas LOV base: reimportar el catalogo no
-- puede duplicar filas, porque LovRepository.findByCode devuelve un unico resultado.
create index if not exists idx_assembly_configuration_code_description
    on assembly_configuration (code, description);
create unique index if not exists ux_assembly_configuration_code
    on assembly_configuration (code);

-- La clave ajena solo en la tabla base; profile_aud lleva la columna sin restriccion,
-- por lo mismo que las otras diez LOV del perfil (ver V20).
alter table profile     add column if not exists assembly_configuration_id bigint;
alter table profile_aud add column if not exists assembly_configuration_id bigint;

alter table profile drop constraint if exists fk_profile_assembly_configuration;
alter table profile add constraint fk_profile_assembly_configuration
    foreign key (assembly_configuration_id) references assembly_configuration;
