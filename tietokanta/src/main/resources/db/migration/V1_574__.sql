ALTER TABLE vv_vayla
  ADD COLUMN tunniste VARCHAR(128),
  ADD COLUMN arvot JSONB,
  ADD COLUMN poistettu BOOLEAN;

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('inspire', 'vaylien-haku');

ALTER TABLE vv_turvalaite
  DROP COLUMN poistettu,
  ADD COLUMN turvalaitenro INT;

