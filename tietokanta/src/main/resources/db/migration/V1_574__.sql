ALTER TABLE vv_vayla
  ADD COLUMN tunniste VARCHAR(128),
  ADD COLUMN vaylanro INT,
  ADD COLUMN arvot JSONB,
  ADD COLUMN poistettu BOOLEAN,
  ADD CONSTRAINT vesivaylien_uniikki_tunniste UNIQUE (tunniste),
  DROP COLUMN "vatu-id";

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('inspire', 'vaylien-haku');

ALTER TABLE vv_turvalaite
  ADD COLUMN turvalaitenro INT,
  ADD COLUMN vaylat INT [];

ALTER INDEX uniikki_tunniste
RENAME TO vesivaylaturvalaite_uniikki_tunniste

