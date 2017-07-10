ALTER TABLE vv_vayla
  ADD COLUMN tunniste VARCHAR(128),
  ADD COLUMN arvot JSONB;

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('inspire', 'vaylien-haku');