INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'kirjaa-tiemerkintatoteuma');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'poista-tiemerkintatoteuma');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'kirjaa-yllapitokohteen-tiemerkintatoteuma');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'poista-yllapitokohteen-tiemerkintatoteuma');

ALTER TABLE tiemerkinnan_yksikkohintainen_toteuma
  ADD COLUMN ulkoinen_id INTEGER;
ALTER TABLE tiemerkinnan_yksikkohintainen_toteuma
  ADD COLUMN luoja INTEGER;

ALTER TABLE tiemerkinnan_yksikkohintainen_toteuma
  ADD CONSTRAINT uniikki_tiemerkinnan_yksikkohintainen_toteuma_luojan_mukaan UNIQUE (ulkoinen_id, luoja);