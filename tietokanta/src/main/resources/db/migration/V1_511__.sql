<<<<<<< HEAD
-- Ylläpitokohdeosalle tarkemmin eritellyt toimenpiteet
ALTER TABLE yllapitokohdeosa ADD COLUMN paallystetyyppi INT; -- ks. koodisto +paallystetyypit+
ALTER TABLE yllapitokohdeosa ADD COLUMN raekoko INT;
ALTER TABLE yllapitokohdeosa ADD COLUMN tyomenetelma INT; -- ks. koodisto +tyomenetelmat+
ALTER TABLE yllapitokohdeosa ADD COLUMN massamaara NUMERIC(10, 2);
=======
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
>>>>>>> ea6e7e9805bc6edd2a72a6ea6edd61d097d38c22
