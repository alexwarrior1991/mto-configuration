-- Migracion inicial: esquema completo de mto-configuration.
--
-- Generada con Hibernate desde las entidades JPA siguiendo el flujo de
-- README_FLYWAY.md (perfil schema-generation) y consolidada como V1.
--
-- Sin prefijo de schema a proposito: el nombre real es configurable
-- (MTO_CONFIGURATION_DB_SCHEMA) y lo resuelve Flyway con su default-schema, que
-- ademas crea el schema si no existe (spring.flyway.create-schemas=true).
-- Fijar "mto_configuration." aqui romperia cualquier despliegue que use otro.
--
-- En una base que YA tiene este esquema, no se ejecuta: baseline-on-migrate la
-- marca como aplicada. Ver README_FLYWAY.md, seccion de adopcion.

create sequence anchorage_seq start with 1 increment by 1;

create sequence anchorage_foundation_seq start with 1 increment by 1;

create sequence anchorage_foundation_type_seq start with 1 increment by 1;

create sequence audit_modified_entity_seq start with 1 increment by 50;

create sequence audit_revision_seq start with 1 increment by 50;

create sequence business_entity_seq start with 1 increment by 1;

create sequence cantilever_seq start with 1 increment by 1;

create sequence cantilever_type_seq start with 1 increment by 1;

create sequence comercial_entity_type_seq start with 1 increment by 1;

create sequence disconnector_seq start with 1 increment by 1;

create sequence disconnector_function_seq start with 1 increment by 1;

create sequence execution_package_seq start with 1 increment by 1;

create sequence foundation_seq start with 1 increment by 1;

create sequence foundation_type_seq start with 1 increment by 1;

create sequence pole_type_seq start with 1 increment by 1;

create sequence portal_seq start with 1 increment by 1;

create sequence portal_type_seq start with 1 increment by 1;

create sequence profile_seq start with 1 increment by 1;

create sequence profile_status_seq start with 1 increment by 1;

create sequence return_support_seq start with 1 increment by 1;

create sequence sectioning_seq start with 1 increment by 1;

create sequence section_insulator_seq start with 1 increment by 1;

create sequence station_seq start with 1 increment by 1;

create sequence steady_arm_seq start with 1 increment by 1;

create sequence steady_arm_type_seq start with 1 increment by 1;

create sequence support_type_seq start with 1 increment by 1;

create sequence track_seq start with 1 increment by 1;

create table anchorage (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     drawing_number bigint,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table anchorage_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     drawing_number bigint,
     id bigint not null,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table anchorage_foundation (
     enabled boolean not null,
     version_number integer not null,
     anchorage_foundation_type_id bigint,
     create_date timestamp(6) not null,
     drawing_number bigint,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table anchorage_foundation_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     anchorage_foundation_type_id bigint,
     create_date timestamp(6),
     drawing_number bigint,
     id bigint not null,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table anchorage_foundation_type (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table anchorage_foundation_type_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     id bigint not null,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table audit_modified_entity (
     id integer not null,
     revision_id integer not null,
     entity_id bigint,
     entity_name varchar(100),
     entity_class_name varchar(300) not null,
     primary key (id)
);

create table audit_revision (
     id integer not null,
     timestamp bigint,
     request_method varchar(20),
     correlation_id varchar(100),
     ip_address varchar(100),
     user_id varchar(100),
     username varchar(150),
     request_uri varchar(500),
     user_agent varchar(500),
     primary key (id)
);

create table business_entity_aud (
     deleted boolean,
     rev integer not null,
     revtype smallint,
     comercial_entity_type_id bigint,
     create_date timestamp(6),
     id bigint not null,
     version_date timestamp(6),
     code varchar(255),
     create_user varchar(255),
     identification_number varchar(255),
     name varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table business_entity (
     deleted boolean not null,
     version_number integer not null,
     comercial_entity_type_id bigint not null,
     create_date timestamp(6) not null,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(255) not null,
     create_user varchar(255) not null,
     identification_number varchar(255) not null unique,
     name varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table cantilever (
     arm_angle numeric(5,3),
     catenary_height numeric(4,3),
     cw_elevation numeric(4,3),
     cw_height numeric(4,3),
     deleted boolean not null,
     insertion_order integer,
     stagger numeric(3,0),
     version_number integer not null,
     wind_deflection numeric(4,3),
     cantilever_type_id bigint,
     create_date timestamp(6) not null,
     id bigint not null,
     profile_id bigint not null,
     version_date timestamp(6) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table cantilever_aud (
     arm_angle numeric(5,3),
     catenary_height numeric(4,3),
     cw_elevation numeric(4,3),
     cw_height numeric(4,3),
     deleted boolean,
     insertion_order integer,
     rev integer not null,
     revtype smallint,
     stagger numeric(3,0),
     wind_deflection numeric(4,3),
     cantilever_type_id bigint,
     create_date timestamp(6),
     id bigint not null,
     profile_id bigint,
     version_date timestamp(6),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table cantilever_type_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     drawing_number bigint,
     id bigint not null,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table cantilever_type (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     drawing_number bigint,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table comercial_entity_type_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     id bigint not null,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table comercial_entity_type (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table disconnector (
     deleted boolean not null,
     onload boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     disconnector_function_id bigint,
     id bigint not null,
     profile_id bigint unique,
     station_id bigint,
     version_date timestamp(6) not null,
     name varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table disconnector_aud (
     deleted boolean,
     onload boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     disconnector_function_id bigint,
     id bigint not null,
     profile_id bigint,
     station_id bigint,
     version_date timestamp(6),
     name varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table disconnector_function (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table disconnector_function_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     id bigint not null,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table execution_package (
     deleted boolean not null,
     enabled boolean not null,
     end_date date not null,
     initial_package boolean not null,
     start_date date not null,
     version_number integer not null,
     company_id bigint,
     create_date timestamp(6) not null,
     id bigint not null,
     length bigint not null,
     version_date timestamp(6) not null,
     name varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table execution_package_aud (
     deleted boolean,
     enabled boolean,
     end_date date,
     initial_package boolean,
     rev integer not null,
     revtype smallint,
     start_date date,
     company_id bigint,
     create_date timestamp(6),
     id bigint not null,
     length bigint,
     version_date timestamp(6),
     name varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table foundation (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     drawing_number bigint,
     foundation_type_id bigint,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table foundation_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     drawing_number bigint,
     foundation_type_id bigint,
     id bigint not null,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table foundation_type_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     id bigint not null,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table foundation_type (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table outbox_message (
     attempts integer not null,
     max_attempts integer not null,
     created_at timestamp(6) with time zone not null,
     next_attempt_at timestamp(6) with time zone,
     published_at timestamp(6) with time zone,
     id uuid not null,
     status varchar(30) not null check ((status in ('PENDING','IN_PROGRESS','PUBLISHED','FAILED'))),
     aggregate_type varchar(100) not null,
     aggregate_id varchar(150) not null,
     event_type varchar(150) not null,
     exchange_name varchar(200) not null,
     routing_key varchar(200) not null,
     last_error varchar(1000),
     payload text not null,
     primary key (id)
);

create table pole_type_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     drawing_number bigint,
     id bigint not null,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table pole_type (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     drawing_number bigint,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table portal (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     drawing_number bigint,
     id bigint not null,
     portal_type_id bigint,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table portal_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     drawing_number bigint,
     id bigint not null,
     portal_type_id bigint,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table portal_type_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     id bigint not null,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table portal_type (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table profile (
     deleted boolean not null,
     insertion_order integer,
     kilometric_point numeric(12,3) not null,
     version_number integer not null,
     anchorage_foundation_id bigint,
     anchorage_id bigint,
     create_date timestamp(6) not null,
     foundation_id bigint,
     id bigint not null,
     pole_type_id bigint,
     portal_id bigint,
     profile_status_id bigint,
     return_support_id bigint,
     sectioning_id bigint,
     track_id bigint,
     version_date timestamp(6) not null,
     profile_id varchar(50) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table profile_aud (
     deleted boolean,
     insertion_order integer,
     kilometric_point numeric(12,3),
     rev integer not null,
     revtype smallint,
     anchorage_foundation_id bigint,
     anchorage_id bigint,
     create_date timestamp(6),
     foundation_id bigint,
     id bigint not null,
     pole_type_id bigint,
     portal_id bigint,
     profile_status_id bigint,
     return_support_id bigint,
     sectioning_id bigint,
     track_id bigint,
     version_date timestamp(6),
     profile_id varchar(50),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table profile_status (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table profile_status_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     id bigint not null,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table return_support_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     drawing_number bigint,
     id bigint not null,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table return_support (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     drawing_number bigint,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table section_insulator (
     deleted boolean not null,
     status boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     id bigint not null,
     station_id bigint,
     version_date timestamp(6) not null,
     name varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table section_insulator_aud (
     deleted boolean,
     rev integer not null,
     revtype smallint,
     status boolean,
     create_date timestamp(6),
     id bigint not null,
     station_id bigint,
     version_date timestamp(6),
     name varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table sectioning (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table sectioning_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     id bigint not null,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table station (
     deleted boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     execution_package_id bigint,
     id bigint not null,
     version_date timestamp(6) not null,
     name varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table station_aud (
     deleted boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     execution_package_id bigint,
     id bigint not null,
     version_date timestamp(6),
     name varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table steady_arm (
     deleted boolean not null,
     version_number integer not null,
     cantilever_id bigint unique,
     create_date timestamp(6) not null,
     id bigint not null,
     length bigint not null check ((length>=0) and (length<=2000)),
     steady_arm_type_id bigint not null,
     version_date timestamp(6) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table steady_arm_aud (
     deleted boolean,
     rev integer not null,
     revtype smallint,
     cantilever_id bigint,
     create_date timestamp(6),
     id bigint not null,
     length bigint,
     steady_arm_type_id bigint,
     version_date timestamp(6),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table steady_arm_type_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     id bigint not null,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table steady_arm_type (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table support_type_aud (
     enabled boolean,
     rev integer not null,
     revtype smallint,
     create_date timestamp(6),
     drawing_number bigint,
     id bigint not null,
     version_date timestamp(6),
     code varchar(10),
     description varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create table support_type (
     enabled boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     drawing_number bigint,
     id bigint not null,
     version_date timestamp(6) not null,
     code varchar(10) not null,
     description varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table track (
     deleted boolean not null,
     status boolean not null,
     version_number integer not null,
     create_date timestamp(6) not null,
     execution_package_id bigint,
     id bigint not null,
     station_id bigint,
     version_date timestamp(6) not null,
     name varchar(200) not null,
     create_user varchar(255) not null,
     version_user varchar(255) not null,
     primary key (id)
);

create table track_aud (
     deleted boolean,
     rev integer not null,
     revtype smallint,
     status boolean,
     create_date timestamp(6),
     execution_package_id bigint,
     id bigint not null,
     station_id bigint,
     version_date timestamp(6),
     name varchar(200),
     create_user varchar(255),
     version_user varchar(255),
     primary key (rev, id)
);

create index IDXno7kd3rjdg70lqxpsu3rmeeqp
    on anchorage (code, description);

create index IDX4rhg1xy1n8pvb1kssg9gyx3eq
    on anchorage_foundation (code, description);

create index IDXlrh5tqpethghyu6jnhjgk5x83
    on anchorage_foundation_type (code, description);

create index idx_audit_modified_entity_revision
    on audit_modified_entity (revision_id);

create index idx_audit_modified_entity_class_id
    on audit_modified_entity (entity_class_name, entity_id);

create index idx_audit_revision_username
    on audit_revision (username);

create index idx_audit_revision_timestamp
    on audit_revision (timestamp);

create index idx_audit_revision_correlation_id
    on audit_revision (correlation_id);

create index IDXfgx7pgaldjwl70lox99i1mvgf
    on cantilever_type (code, description);

create index IDX4lbpbsqyynunxyafb83vu65gj
    on comercial_entity_type (code, description);

create index IDXry5qaajiku5mklkxfv5wmksnp
    on disconnector_function (code, description);

create index IDXbae7xffl8qlubs8fc4px666o9
    on foundation (code, description);

create index IDX2jxiltpwijdo3gw6uot9jhrgv
    on foundation_type (code, description);

create index IDXntgx873l87u0h9m815pmt50xj
    on pole_type (code, description);

create index IDX5egp6dt82063yfe31dc39xsy8
    on portal (code, description);

create index IDX3myhaswn2yl9mth1dcx5ggm8i
    on portal_type (code, description);

create index IDX_PROFILE_TRACK_KP_ID
    on profile (track_id, kilometric_point, id);

create index IDXa3kpaik8ralcgfpbrba2gp1lj
    on profile_status (code, description);

create index IDXn358crk98w2ecpdd2ud7syojc
    on return_support (code, description);

create index IDXj6al4iyb5tnpt21hvw5eatkkv
    on sectioning (code, description);

create index IDXdyb6mj5vhvixlqd0q0ct1sfid
    on steady_arm_type (code, description);

create index IDXpmx60r7u37c9f8rfyyuck1nvm
    on support_type (code, description);

alter table if exists anchorage_aud
    add constraint FK480s4aby8xu81ldh9cm32td4o
    foreign key (rev)
    references audit_revision;

alter table if exists anchorage_foundation
    add constraint FK4gq4b2jo0obybdtygys7u81o7
    foreign key (anchorage_foundation_type_id)
    references anchorage_foundation_type;

alter table if exists anchorage_foundation_aud
    add constraint FKu1qsbsuu2rqd90m4nd85d4ed
    foreign key (rev)
    references audit_revision;

alter table if exists anchorage_foundation_type_aud
    add constraint FKkau8qiiua672a9kminrup1j07
    foreign key (rev)
    references audit_revision;

alter table if exists audit_modified_entity
    add constraint FKfxi7xl3c3yry4lb2perk09bus
    foreign key (revision_id)
    references audit_revision;

alter table if exists business_entity_aud
    add constraint FK794ode7frdsbxdv3q1d5o7a2u
    foreign key (rev)
    references audit_revision;

alter table if exists business_entity
    add constraint FK6kg0uo0iwj9ejbvkvgpardbl5
    foreign key (comercial_entity_type_id)
    references comercial_entity_type;

alter table if exists cantilever
    add constraint FKgex1bpqqkxn69cbhq924p3mtq
    foreign key (cantilever_type_id)
    references cantilever_type;

alter table if exists cantilever
    add constraint FKo7a01mrxuin4wx8p3wlkkqr5n
    foreign key (profile_id)
    references profile;

alter table if exists cantilever_aud
    add constraint FKicjfrvjvjcek2s56khgvwa2va
    foreign key (rev)
    references audit_revision;

alter table if exists cantilever_type_aud
    add constraint FKjcm3olyq513yxlbj4k8cpdwg5
    foreign key (rev)
    references audit_revision;

alter table if exists comercial_entity_type_aud
    add constraint FKfqq398i56koiasyidua1hlq1v
    foreign key (rev)
    references audit_revision;

alter table if exists disconnector
    add constraint FKiwnkdtoepn7t3hrusxxmny2hg
    foreign key (disconnector_function_id)
    references disconnector_function;

alter table if exists disconnector
    add constraint FKewfvpo5v4hv7wfb1bs0w68irp
    foreign key (profile_id)
    references profile;

alter table if exists disconnector
    add constraint FKm6fvwp6jrp5ye78goevkasn5r
    foreign key (station_id)
    references station;

alter table if exists disconnector_aud
    add constraint FK1x2pu3k4ttebu6626qpq85uvn
    foreign key (rev)
    references audit_revision;

alter table if exists disconnector_function_aud
    add constraint FK1kfrpo7colu2qmvbke0nwgyvt
    foreign key (rev)
    references audit_revision;

alter table if exists execution_package
    add constraint FKqxv4t1an0rsbxun3poarej8a0
    foreign key (company_id)
    references business_entity;

alter table if exists execution_package_aud
    add constraint FKbyxo0ybl8bwlks3atgacf42pw
    foreign key (rev)
    references audit_revision;

alter table if exists foundation
    add constraint FKbq59mcwy4kpbq8oprolxc0xcw
    foreign key (foundation_type_id)
    references foundation_type;

alter table if exists foundation_aud
    add constraint FK77l350thq1iypoi4fi9viceev
    foreign key (rev)
    references audit_revision;

alter table if exists foundation_type_aud
    add constraint FK42qeoyrcyvu4iwfgwhf30qo5g
    foreign key (rev)
    references audit_revision;

alter table if exists pole_type_aud
    add constraint FK6qmt6cmv20a6cxo2ejd3j1shu
    foreign key (rev)
    references audit_revision;

alter table if exists portal
    add constraint FKjrbvtgtx9b96jh0ojt288rknh
    foreign key (portal_type_id)
    references portal_type;

alter table if exists portal_aud
    add constraint FKb5wkihn5djuqanhnxy2evrby5
    foreign key (rev)
    references audit_revision;

alter table if exists portal_type_aud
    add constraint FK5dor8g8fyfv4k1kk101jdippv
    foreign key (rev)
    references audit_revision;

alter table if exists profile
    add constraint FKn5uwq2t8hyvun4gmcmvq0x5me
    foreign key (anchorage_id)
    references anchorage;

alter table if exists profile
    add constraint FKaln7kxfj78q9hu1xrdylkxa24
    foreign key (anchorage_foundation_id)
    references anchorage_foundation;

alter table if exists profile
    add constraint FKm1kf0ev03ia75o8n7gfrn5sr7
    foreign key (foundation_id)
    references foundation;

alter table if exists profile
    add constraint FKr853ggolvbidit3tlsj0814f4
    foreign key (pole_type_id)
    references pole_type;

alter table if exists profile
    add constraint FKotl8x57c7br9bvyaiktn9afu8
    foreign key (portal_id)
    references portal;

alter table if exists profile
    add constraint FKmkxf16k0uxpsgkcmo1jxmn76l
    foreign key (profile_status_id)
    references profile_status;

alter table if exists profile
    add constraint FKfuhxtpa0wtoa8nx9xnflulqcy
    foreign key (return_support_id)
    references return_support;

alter table if exists profile
    add constraint FKth1ygx3b5e7qg8flryaf2kx13
    foreign key (sectioning_id)
    references sectioning;

alter table if exists profile
    add constraint FKhkejgdgonad25d0dy32rjbkat
    foreign key (track_id)
    references track;

alter table if exists profile_aud
    add constraint FK9rcoicw8k6mot9e75vgp6j93o
    foreign key (rev)
    references audit_revision;

alter table if exists profile_status_aud
    add constraint FKpui818bv5nmmhbefjukjr4wr0
    foreign key (rev)
    references audit_revision;

alter table if exists return_support_aud
    add constraint FKfnuvgtqbdycxfo0u9iiyn0xpc
    foreign key (rev)
    references audit_revision;

alter table if exists section_insulator
    add constraint FK8shwe4e8m4ddr01fouo4vi7q7
    foreign key (station_id)
    references station;

alter table if exists section_insulator_aud
    add constraint FKf7hm1f04q2n5lxbt0v9qan3t3
    foreign key (rev)
    references audit_revision;

alter table if exists sectioning_aud
    add constraint FKkcywl96ejxuoerv1fgjgyhd7b
    foreign key (rev)
    references audit_revision;

alter table if exists station
    add constraint FK3scw3c53iu9e4gctrthol8h8b
    foreign key (execution_package_id)
    references execution_package;

alter table if exists station_aud
    add constraint FKc9ika3tbw2elei1gv38p5e138
    foreign key (rev)
    references audit_revision;

alter table if exists steady_arm
    add constraint FK893a1p7vwj6av9ffeshpe00tr
    foreign key (cantilever_id)
    references cantilever;

alter table if exists steady_arm
    add constraint FKaohauoy0jrexk7me9vblyqhst
    foreign key (steady_arm_type_id)
    references steady_arm_type;

alter table if exists steady_arm_aud
    add constraint FKhx7atvkcal649btmgojtn9ua6
    foreign key (rev)
    references audit_revision;

alter table if exists steady_arm_type_aud
    add constraint FKdixjipe43w6kwtntc72ug03ce
    foreign key (rev)
    references audit_revision;

alter table if exists support_type_aud
    add constraint FK50geiv5spckd8ifci4tp4gjg0
    foreign key (rev)
    references audit_revision;

alter table if exists track
    add constraint FK9w5myvtrnngdhv8kb3yhclpcl
    foreign key (execution_package_id)
    references execution_package;

alter table if exists track
    add constraint FK444e6o3gmxnyohsqm9prrlwdh
    foreign key (station_id)
    references station;

alter table if exists track_aud
    add constraint FK475oske4nidn4gmfsucn1ktxi
    foreign key (rev)
    references audit_revision;
