-- Un perfil puede llevar VARIOS aparatos de seccionamiento y alimentacion, no uno.
--
-- Tercera y ultima del patron, despues de sectioning (V14) y anchorage (V15). Las cuatro
-- combinaciones que trae el origen son electricamente sensatas: 'Disc SECT-I' es un
-- disconnector mas un aislador de seccion, 'LoadB/NS FS-1' un load breaker de zona neutra
-- mas las conexiones de feeder, 'FS-1 VoltageD' esas conexiones mas un detector de tension.
--
-- Que aparezcan DUPLICADAS en vias paralelas —la misma celda en 'HR Track 1.1' fila 37 y en
-- 'HR Track 2' fila 37— es lo que descarta la errata: un punto de seccionamiento que cruza
-- varias vias se anota igual en todas, y un desliz de tecleo seria aislado.
--
-- La columna se llama por su PAPEL (sectioning_feeding) y apunta al catalogo
-- disconnector_function, que es el que define esos 22 codigos. Se mantiene ese nombre:
-- renombrarla ahora solo cambiaria de sitio la sorpresa.
create table if not exists profile_sectioning_feeding (
    profile_id            bigint not null,
    sectioning_feeding_id bigint not null,
    primary key (profile_id, sectioning_feeding_id)
);

ALTER TABLE profile_sectioning_feeding DROP CONSTRAINT IF EXISTS fk_psf_profile;
ALTER TABLE profile_sectioning_feeding ADD CONSTRAINT fk_psf_profile
    FOREIGN KEY (profile_id) REFERENCES profile;

ALTER TABLE profile_sectioning_feeding DROP CONSTRAINT IF EXISTS fk_psf_disconnector_function;
ALTER TABLE profile_sectioning_feeding ADD CONSTRAINT fk_psf_disconnector_function
    FOREIGN KEY (sectioning_feeding_id) REFERENCES disconnector_function;

create index if not exists idx_psf_sectioning_feeding
    on profile_sectioning_feeding (sectioning_feeding_id);

-- Gemela de Envers, igual que las de V14 y V15: una N:M audita la PERTENENCIA, y cada alta
-- o baja del par es su propia revision. DisconnectorFunction es NOT_AUDITED (es un
-- catalogo), asi que aqui solo viaja su id.
create table if not exists profile_sectioning_feeding_aud (
    rev                   integer not null,
    profile_id            bigint  not null,
    sectioning_feeding_id bigint  not null,
    revtype               smallint,
    primary key (rev, profile_id, sectioning_feeding_id)
);

ALTER TABLE profile_sectioning_feeding_aud DROP CONSTRAINT IF EXISTS fk_psf_aud_rev;
ALTER TABLE profile_sectioning_feeding_aud ADD CONSTRAINT fk_psf_aud_rev
    FOREIGN KEY (rev) REFERENCES audit_revision;

-- Lo que ya estaba cargado se conserva. Mismo guardado que en V14 y V15: la columna puede
-- no existir cuando el esquema se crea desde las entidades actuales, EXECUTE para que
-- PL/pgSQL no analice la sentencia si la rama no se toma, y to_regclass en vez del cast
-- porque el cast lanza justo en el caso del que uno se protege.
DO $$
BEGIN
    IF to_regclass('profile') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_attribute
                    WHERE attrelid = to_regclass('profile')
                      AND attname = 'sectioning_feeding_id'
                      AND attnum > 0
                      AND NOT attisdropped) THEN
        EXECUTE 'insert into profile_sectioning_feeding (profile_id, sectioning_feeding_id)'
             || ' select id, sectioning_feeding_id from profile'
             || ' where sectioning_feeding_id is not null'
             || ' on conflict do nothing';
    END IF;
END $$;

alter table profile     drop column if exists sectioning_feeding_id;
alter table profile_aud drop column if exists sectioning_feeding_id;
