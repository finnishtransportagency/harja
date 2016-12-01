ALTER TABLE suljettu_tieosuus
  RENAME TO tietyomaa;

ALTER TABLE tietyomaa
  RENAME CONSTRAINT suljettu_tieosuus_jarjestelma_osuus_id_key TO tietyomaa_jarjestelma_osuus_id_key;
ALTER TABLE tietyomaa
  RENAME CONSTRAINT suljettu_tieosuus_geom_index TO tietyomaa_geom_index;
ALTER TABLE tietyomaa
  RENAME CONSTRAINT suljettu_tieosuus_envelope_idx TO tietyomaa_envelope_idx;

UPDATE integraatio
SET nimi = 'kirjaa-tietyomaa'
WHERE jarjestelma = 'api' AND nimi = 'kirjaa-suljettu-tieosuus';
UPDATE integraatio
SET nimi = 'poista-tietyomaa'
WHERE jarjestelma = 'api' AND nimi = 'poista-suljettu-tieosuus';