ALTER TABLE suljettu_tieosuus
  RENAME TO tietyomaa;

ALTER TABLE tietyomaa
  RENAME CONSTRAINT suljettu_tieosuus_yllapitokohde_fkey TO tietyomaa_yllapitokohde_fkey;
ALTER TABLE tietyomaa
  RENAME CONSTRAINT suljettu_tieosuus_kirjaaja_fkey TO tietyomaa_kirjaaja_fkey;
ALTER TABLE tietyomaa
  RENAME CONSTRAINT suljettu_tieosuus_poistaja_fkey TO tietyomaa_poistaja_fkey;

ALTER INDEX suljettu_tieosuus_jarjestelma_osuus_id_key
RENAME TO tietyomaa_jarjestelma_osuus_id_key;
ALTER INDEX suljettu_tieosuus_geom_index
RENAME TO tietyomaa_geom_index;
ALTER INDEX suljettu_tieosuus_envelope_idx
RENAME TO tietyomaa_envelope_idx;

UPDATE integraatio
SET nimi = 'kirjaa-tietyomaa'
WHERE jarjestelma = 'api' AND nimi = 'kirjaa-suljettu-tieosuus';
UPDATE integraatio
SET nimi = 'poista-tietyomaa'
WHERE jarjestelma = 'api' AND nimi = 'poista-suljettu-tieosuus';