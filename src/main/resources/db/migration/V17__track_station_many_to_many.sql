-- Una via puede atravesar VARIAS estaciones.
--
-- 'TRACK 1' de EP4 es una via larga: un tramo cae dentro de ZIC, otro dentro de BIN y
-- otro dentro de HAD, y sigue siendo UNA via. Con la clave ajena unica solo cabia una
-- de las tres, y las otras dos se perdian. La alternativa era partir la via en tres
-- filas por rango de filas de la hoja, y eso es contar una via como tres.
--
-- Lo que este modelo NO guarda es que tramo pertenece a cada estacion: dice que la via
-- pasa por ZIC, BIN y HAD, no por donde empieza cada una. Es una decision consciente:
-- el dato de origen no marca los limites de forma fiable (de las 176 vias medidas, 31
-- tienen el KP no monotono y una llega a un KP de 1.110.546, un dedazo por 110.546),
-- asi que un limite declarado seria una precision inventada.
--
-- Cuarta N:M del dominio, con la misma forma que profile_sectioning (V14),
-- profile_anchorage (V15) y profile_sectioning_feeding (V16).
create table if not exists track_station (
    track_id   bigint not null,
    station_id bigint not null,
    primary key (track_id, station_id)
);

ALTER TABLE track_station DROP CONSTRAINT IF EXISTS fk_track_station_track;
ALTER TABLE track_station ADD CONSTRAINT fk_track_station_track
    FOREIGN KEY (track_id) REFERENCES track;

ALTER TABLE track_station DROP CONSTRAINT IF EXISTS fk_track_station_station;
ALTER TABLE track_station ADD CONSTRAINT fk_track_station_station
    FOREIGN KEY (station_id) REFERENCES station;

create index if not exists idx_track_station_station
    on track_station (station_id);

-- Gemela de Envers: una N:M audita la PERTENENCIA, y cada alta o baja del par es su
-- propia revision.
create table if not exists track_station_aud (
    rev        integer not null,
    track_id   bigint  not null,
    station_id bigint  not null,
    revtype    smallint,
    primary key (rev, track_id, station_id)
);

ALTER TABLE track_station_aud DROP CONSTRAINT IF EXISTS fk_track_station_aud_rev;
ALTER TABLE track_station_aud ADD CONSTRAINT fk_track_station_aud_rev
    FOREIGN KEY (rev) REFERENCES audit_revision;

-- Traspaso de lo que ya hubiera. Guardado con to_regclass y EXECUTE por lo mismo que
-- V14, V15 y V16: FlywayLegacyAdoptionIT construye un esquema que solo tiene
-- outbox_message, y ahi 'track' se resuelve por search_path a otro esquema donde la
-- columna station_id ya no existe. Un cast 'track'::regclass lanzaria antes del IF.
DO $$
BEGIN
    IF to_regclass('track') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_attribute
                    WHERE attrelid = to_regclass('track')
                      AND attname = 'station_id'
                      AND attnum > 0
                      AND NOT attisdropped) THEN
        EXECUTE 'insert into track_station (track_id, station_id)'
             || ' select id, station_id from track where station_id is not null'
             || ' on conflict do nothing';
    END IF;
END $$;

ALTER TABLE track     DROP COLUMN IF EXISTS station_id;
ALTER TABLE track_aud DROP COLUMN IF EXISTS station_id;
