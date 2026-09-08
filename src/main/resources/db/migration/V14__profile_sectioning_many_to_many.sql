-- Un perfil puede llevar VARIOS seccionamientos a la vez, no uno.
--
-- Es corriente en estaciones: un perfil puede ser 'A/S P50', otro solo 'A/S' y otro
-- tres a la vez. El modelo tenia profile.sectioning_id, una clave ajena que solo admite
-- uno, asi que la celda con dos valores no era un error de tecleo: era el dominio que no
-- cabia en el esquema. Los workbooks traen 35 filas asi, y seran mas.
--
-- Solo 'sectioning' se hace N:M. Las demas listas de valores del perfil (anchorage,
-- return_support, sectioning_feeding...) llevan una sola por perfil, asi que ampliarlas
-- seria complicar el modelo sin motivo.
create table if not exists profile_sectioning (
    profile_id    bigint not null,
    sectioning_id bigint not null,
    primary key (profile_id, sectioning_id)
);

ALTER TABLE profile_sectioning DROP CONSTRAINT IF EXISTS fk_profile_sectioning_profile;
ALTER TABLE profile_sectioning ADD CONSTRAINT fk_profile_sectioning_profile
    FOREIGN KEY (profile_id) REFERENCES profile;

ALTER TABLE profile_sectioning DROP CONSTRAINT IF EXISTS fk_profile_sectioning_sectioning;
ALTER TABLE profile_sectioning ADD CONSTRAINT fk_profile_sectioning_sectioning
    FOREIGN KEY (sectioning_id) REFERENCES sectioning;

-- Buscar "perfiles que tienen este seccionamiento" recorre la tabla por el otro lado.
create index if not exists idx_profile_sectioning_sectioning
    on profile_sectioning (sectioning_id);

-- Gemela de Envers. Una N:M audita la PERTENENCIA: cada alta o baja del par es su
-- propia revision, de ahi que la clave incluya rev y que revtype diga si se anadio o
-- se quito. Sectioning es NOT_AUDITED (es un catalogo), asi que aqui solo viaja su id.
create table if not exists profile_sectioning_aud (
    rev           integer not null,
    profile_id    bigint  not null,
    sectioning_id bigint  not null,
    revtype       smallint,
    primary key (rev, profile_id, sectioning_id)
);

ALTER TABLE profile_sectioning_aud DROP CONSTRAINT IF EXISTS fk_profile_sectioning_aud_rev;
ALTER TABLE profile_sectioning_aud ADD CONSTRAINT fk_profile_sectioning_aud_rev
    FOREIGN KEY (rev) REFERENCES audit_revision;

-- Lo que ya estaba cargado se conserva: cada perfil con seccionamiento pasa a tener uno.
insert into profile_sectioning (profile_id, sectioning_id)
select id, sectioning_id from profile where sectioning_id is not null
on conflict do nothing;

-- Y se retira la columna, para que no queden dos sitios donde mirar.
alter table profile     drop column if exists sectioning_id;
alter table profile_aud drop column if exists sectioning_id;
