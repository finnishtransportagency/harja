ALTER TABLE vv_vayla
  ADD COLUMN tunniste VARCHAR(128),
  ADD COLUMN vaylanro INT,
  ADD COLUMN arvot JSONB,
  ADD COLUMN poistettu BOOLEAN,
  ADD CONSTRAINT vesivaylien_uniikki_tunniste UNIQUE (tunniste),
  DROP COLUMN "vatu-id";

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('inspire', 'vaylien-haku');


CREATE TYPE VV_TURVALAITETYYPPI AS ENUM (
  'tuntematon',
  'merimajakka',
  'sektoriloisto',
  'linjamerkki',
  'suuntaloisto',
  'apuloisto',
  'muu merkki',
  'reunamerkki',
  'tutkamerkki',
  'poiju',
  'viitta',
  'tunnusmajakka',
  'kummeli');

ALTER TABLE vv_turvalaite
  ADD COLUMN turvalaitenro INT,
  ADD COLUMN vaylat INT [],
  ADD COLUMN kiintea BOOLEAN,
  DROP COLUMN tyyppi,
  ADD COLUMN tyyppi VV_TURVALAITETYYPPI,
  DROP COLUMN vayla;


DROP TYPE TURVALAITTEEN_TYYPPI;

ALTER INDEX uniikki_tunniste
RENAME TO vesivaylaturvalaite_uniikki_tunniste;