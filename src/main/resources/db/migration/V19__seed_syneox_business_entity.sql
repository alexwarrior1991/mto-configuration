-- Siembra la empresa que declaran los once paquetes de topology.yml.
--
-- Hasta aqui business_entity venia de "un maestro externo" que este repositorio no tiene:
-- ni migracion, ni servicio, ni endpoint que la escriba. El efecto practico era que una
-- base recien migrada NO podia importar el maestro de perfiles —
-- InfrastructureUpsertService.resolveCompany traduce el NIF de cada paquete contra
-- business_entity y falla si no esta— y habia que insertar la fila a mano antes de cada
-- carga, en cada entorno.
--
-- Los once paquetes declaran el MISMO NIF, B10744258, asi que es una sola fila.
--
-- Mismo criterio que la siembra de profile_status en V12: SQL directo e idempotente por
-- WHERE NOT EXISTS. Envers no ve estas altas, de modo que los *_aud se quedan sin
-- revision inicial; es aceptable en un catalogo cerrado que nadie va a modificar, y es
-- justo lo que evita que un INSERT en la migracion dependa de que exista una revision.

-- 1. El tipo de entidad comercial.
--
-- La tabla estaba VACIA y business_entity.comercial_entity_type_id es NOT NULL, asi que
-- sin esto no hay empresa que insertar. Van los TRES valores del enum
-- ComercialEntityTypeValue y no solo el que hace falta: es un catalogo cerrado, y
-- isCustoms() e isConsignee() estan hoy igual de inservibles que lo estaba
-- isRailwayCompany(), porque comparan contra un codigo que no existe en ninguna fila.
INSERT INTO comercial_entity_type (id, code, description, enabled,
                                   create_date, create_user,
                                   version_date, version_user, version_number)
SELECT nextval('comercial_entity_type_seq'), seed.code, seed.description, true,
       now(), 'system', now(), 'system', 1
FROM (VALUES
        ('CONSIGNEE',       'COMMERCIAL_ENTITY_TYPE_CONSIGNEE'),
        ('CUSTOMS',         'COMMERCIAL_ENTITY_TYPE_CUSTOMS'),
        ('RAILWAY_COMPANY', 'COMMERCIAL_ENTITY_TYPE_RAILWAY_COMPANY')
     ) AS seed(code, description)
WHERE NOT EXISTS (
    SELECT 1 FROM comercial_entity_type existing WHERE existing.code = seed.code
);

-- 2. Syneox.
--
-- La condicion mira el NIF y no el nombre ni el id: es la clave natural —la columna es
-- UNIQUE desde V1— y es por lo que pregunta el importador
-- (BusinessEntityRepository.findByIdentificationNumber). Con eso, reaplicar la migracion
-- sobre una base donde la empresa ya se metio a mano no duplica ni choca.
INSERT INTO business_entity (id, name, code, identification_number,
                             comercial_entity_type_id, deleted,
                             create_date, create_user,
                             version_date, version_user, version_number)
SELECT nextval('business_entity_seq'), 'Syneox', 'SYNEOX', 'B10744258',
       (SELECT id FROM comercial_entity_type WHERE code = 'RAILWAY_COMPANY'),
       false, now(), 'system', now(), 'system', 1
WHERE NOT EXISTS (
    SELECT 1 FROM business_entity existing
    WHERE existing.identification_number = 'B10744258'
);
