ALTER TABLE vv_vayla
  ADD COLUMN tunniste VARCHAR(128),
  ADD COLUMN arvot JSONB,
  ADD COLUMN poistettu BOOLEAN,
  ADD CONSTRAINT vesivaylien_uniikki_tunniste UNIQUE (tunniste),
  DROP COLUMN "vatu-id",
  ALTER COLUMN "tyyppi" DROP NOT NULL;


INSERT INTO integraatio (jarjestelma, nimi) VALUES ('inspire', 'vaylien-haku');

ALTER TABLE vv_turvalaite
  ADD COLUMN turvalaitenro INT;

ALTER INDEX uniikki_tunniste
RENAME TO vesivaylaturvalaite_uniikki_tunniste

