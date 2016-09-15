-- Partitioi tarkastustaulu

-- Päivitetään foreign keyt
-- Huom: nämä eivät voi viitata tarkastustyypin tauluun, koska
-- esim. soratiemittaus voi olla tarkastuksekssa, jonka tyyppi ei ole
-- soratie.
ALTER TABLE soratiemittaus DROP CONSTRAINT soratiemittaus_tarkastus_fkey;
ALTER TABLE talvihoitomittaus DROP CONSTRAINT talvihoitomittaus_tarkastus_fkey;
ALTER TABLE tarkastus_kommentti DROP CONSTRAINT tarkastus_kommentti_tarkastus_fkey;
ALTER TABLE tarkastus_laatupoikkeama DROP CONSTRAINT tarkastus_laatupoikkeama_tarkastus_fkey;
ALTER TABLE tarkastus_liite DROP CONSTRAINT tarkastus_liite_tarkastus_fkey;
ALTER TABLE tarkastus_vakiohavainto DROP CONSTRAINT tarkastus_vakiohavainto_tarkastus_fkey;


-- Kopioidaan nykyiset datat uusiin tauluihin
CREATE TEMPORARY TABLE tarkastus_temp ON COMMIT DROP AS SELECT * FROM tarkastus;

-- Tyhjennetään tarkastukset
TRUNCATE tarkastus CONTINUE IDENTITY;

-- Luo taulut kvartaaleittain
CREATE TABLE tarkastus_ennen_2015 ( CHECK( aika < '2015-01-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2015_q1 ( CHECK( aika >= '2015-01-01'::date AND aika < '2015-04-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2015_q2 ( CHECK( aika >= '2015-04-01'::date AND aika < '2015-07-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2015_q3 ( CHECK( aika >= '2015-07-01'::date AND aika < '2015-10-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2015_q4 ( CHECK( aika >= '2015-10-01'::date AND aika < '2016-01-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2016_q1 ( CHECK( aika >= '2016-01-01'::date AND aika < '2016-04-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2016_q2 ( CHECK( aika >= '2016-04-01'::date AND aika < '2016-07-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2016_q3 ( CHECK( aika >= '2016-07-01'::date AND aika < '2016-10-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2016_q4 ( CHECK( aika >= '2016-10-01'::date AND aika < '2017-01-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2017_q1 ( CHECK( aika >= '2017-01-01'::date AND aika < '2017-04-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2017_q2 ( CHECK( aika >= '2017-04-01'::date AND aika < '2017-07-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2017_q3 ( CHECK( aika >= '2017-07-01'::date AND aika < '2017-10-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2017_q4 ( CHECK( aika >= '2017-10-01'::date AND aika < '2018-01-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2018_q1 ( CHECK( aika >= '2018-01-01'::date AND aika < '2018-04-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2018_q2 ( CHECK( aika >= '2018-04-01'::date AND aika < '2018-07-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2018_q3 ( CHECK( aika >= '2018-07-01'::date AND aika < '2018-10-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2018_q4 ( CHECK( aika >= '2018-10-01'::date AND aika < '2019-01-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2019_q1 ( CHECK( aika >= '2019-01-01'::date AND aika < '2019-04-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2019_q2 ( CHECK( aika >= '2019-04-01'::date AND aika < '2019-07-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2019_q3 ( CHECK( aika >= '2019-07-01'::date AND aika < '2019-10-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2019_q4 ( CHECK( aika >= '2019-10-01'::date AND aika < '2020-01-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2020_q1 ( CHECK( aika >= '2020-01-01'::date AND aika < '2020-04-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2020_q2 ( CHECK( aika >= '2020-04-01'::date AND aika < '2020-07-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2020_q3 ( CHECK( aika >= '2020-07-01'::date AND aika < '2020-10-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2020_q4 ( CHECK( aika >= '2020-10-01'::date AND aika < '2021-01-01'::date) ) INHERITS (tarkastus);

-- Luo indeksit osille
CREATE UNIQUE INDEX tarkastus_ennen_2015_id_idx ON tarkastus_ennen_2015 (id);
CREATE UNIQUE INDEX tarkastus_2015_q1_id_idx ON tarkastus_2015_q1 (id);
CREATE UNIQUE INDEX tarkastus_2015_q2_id_idx ON tarkastus_2015_q2 (id);
CREATE UNIQUE INDEX tarkastus_2015_q3_id_idx ON tarkastus_2015_q3 (id);
CREATE UNIQUE INDEX tarkastus_2015_q4_id_idx ON tarkastus_2015_q4 (id);
CREATE UNIQUE INDEX tarkastus_2016_q1_id_idx ON tarkastus_2016_q1 (id);
CREATE UNIQUE INDEX tarkastus_2016_q2_id_idx ON tarkastus_2016_q2 (id);
CREATE UNIQUE INDEX tarkastus_2016_q3_id_idx ON tarkastus_2016_q3 (id);
CREATE UNIQUE INDEX tarkastus_2016_q4_id_idx ON tarkastus_2016_q4 (id);
CREATE UNIQUE INDEX tarkastus_2017_q1_id_idx ON tarkastus_2017_q1 (id);
CREATE UNIQUE INDEX tarkastus_2017_q2_id_idx ON tarkastus_2017_q2 (id);
CREATE UNIQUE INDEX tarkastus_2017_q3_id_idx ON tarkastus_2017_q3 (id);
CREATE UNIQUE INDEX tarkastus_2017_q4_id_idx ON tarkastus_2017_q4 (id);
CREATE UNIQUE INDEX tarkastus_2018_q1_id_idx ON tarkastus_2018_q1 (id);
CREATE UNIQUE INDEX tarkastus_2018_q2_id_idx ON tarkastus_2018_q2 (id);
CREATE UNIQUE INDEX tarkastus_2018_q3_id_idx ON tarkastus_2018_q3 (id);
CREATE UNIQUE INDEX tarkastus_2018_q4_id_idx ON tarkastus_2018_q4 (id);
CREATE UNIQUE INDEX tarkastus_2019_q1_id_idx ON tarkastus_2019_q1 (id);
CREATE UNIQUE INDEX tarkastus_2019_q2_id_idx ON tarkastus_2019_q2 (id);
CREATE UNIQUE INDEX tarkastus_2019_q3_id_idx ON tarkastus_2019_q3 (id);
CREATE UNIQUE INDEX tarkastus_2019_q4_id_idx ON tarkastus_2019_q4 (id);
CREATE UNIQUE INDEX tarkastus_2020_q1_id_idx ON tarkastus_2020_q1 (id);
CREATE UNIQUE INDEX tarkastus_2020_q2_id_idx ON tarkastus_2020_q2 (id);
CREATE UNIQUE INDEX tarkastus_2020_q3_id_idx ON tarkastus_2020_q3 (id);
CREATE UNIQUE INDEX tarkastus_2020_q4_id_idx ON tarkastus_2020_q4 (id);
CREATE INDEX tarkastus_ennen_2015_urakka_idx ON tarkastus_ennen_2015 (urakka);
CREATE INDEX tarkastus_2015_q1_urakka_idx ON tarkastus_2015_q1 (urakka);
CREATE INDEX tarkastus_2015_q2_urakka_idx ON tarkastus_2015_q2 (urakka);
CREATE INDEX tarkastus_2015_q3_urakka_idx ON tarkastus_2015_q3 (urakka);
CREATE INDEX tarkastus_2015_q4_urakka_idx ON tarkastus_2015_q4 (urakka);
CREATE INDEX tarkastus_2016_q1_urakka_idx ON tarkastus_2016_q1 (urakka);
CREATE INDEX tarkastus_2016_q2_urakka_idx ON tarkastus_2016_q2 (urakka);
CREATE INDEX tarkastus_2016_q3_urakka_idx ON tarkastus_2016_q3 (urakka);
CREATE INDEX tarkastus_2016_q4_urakka_idx ON tarkastus_2016_q4 (urakka);
CREATE INDEX tarkastus_2017_q1_urakka_idx ON tarkastus_2017_q1 (urakka);
CREATE INDEX tarkastus_2017_q2_urakka_idx ON tarkastus_2017_q2 (urakka);
CREATE INDEX tarkastus_2017_q3_urakka_idx ON tarkastus_2017_q3 (urakka);
CREATE INDEX tarkastus_2017_q4_urakka_idx ON tarkastus_2017_q4 (urakka);
CREATE INDEX tarkastus_2018_q1_urakka_idx ON tarkastus_2018_q1 (urakka);
CREATE INDEX tarkastus_2018_q2_urakka_idx ON tarkastus_2018_q2 (urakka);
CREATE INDEX tarkastus_2018_q3_urakka_idx ON tarkastus_2018_q3 (urakka);
CREATE INDEX tarkastus_2018_q4_urakka_idx ON tarkastus_2018_q4 (urakka);
CREATE INDEX tarkastus_2019_q1_urakka_idx ON tarkastus_2019_q1 (urakka);
CREATE INDEX tarkastus_2019_q2_urakka_idx ON tarkastus_2019_q2 (urakka);
CREATE INDEX tarkastus_2019_q3_urakka_idx ON tarkastus_2019_q3 (urakka);
CREATE INDEX tarkastus_2019_q4_urakka_idx ON tarkastus_2019_q4 (urakka);
CREATE INDEX tarkastus_2020_q1_urakka_idx ON tarkastus_2020_q1 (urakka);
CREATE INDEX tarkastus_2020_q2_urakka_idx ON tarkastus_2020_q2 (urakka);
CREATE INDEX tarkastus_2020_q3_urakka_idx ON tarkastus_2020_q3 (urakka);
CREATE INDEX tarkastus_2020_q4_urakka_idx ON tarkastus_2020_q4 (urakka);
CREATE UNIQUE INDEX tarkastus_ennen_2015_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_ennen_2015 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2015_q1_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2015_q1 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2015_q2_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2015_q2 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2015_q3_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2015_q3 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2015_q4_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2015_q4 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2016_q1_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2016_q1 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2016_q2_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2016_q2 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2016_q3_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2016_q3 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2016_q4_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2016_q4 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2017_q1_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2017_q1 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2017_q2_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2017_q2 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2017_q3_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2017_q3 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2017_q4_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2017_q4 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2018_q1_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2018_q1 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2018_q2_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2018_q2 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2018_q3_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2018_q3 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2018_q4_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2018_q4 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2019_q1_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2019_q1 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2019_q2_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2019_q2 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2019_q3_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2019_q3 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2019_q4_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2019_q4 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2020_q1_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2020_q1 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2020_q2_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2020_q2 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2020_q3_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2020_q3 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2020_q4_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2020_q4 (ulkoinen_id,luoja,tyyppi);

-- Luo insert trigger
CREATE OR REPLACE FUNCTION tarkastus_insert() RETURNS trigger AS $$
DECLARE
  aika date;
BEGIN
  aika := NEW.aika;
  IF aika < '2015-01-01'::date THEN 
    INSERT INTO tarkastus_ennen_2015 VALUES (NEW.*);
  ELSIF aika >= '2015-01-01'::date AND aika < '2015-04-01'::date THEN 
    INSERT INTO tarkastus_2015_q1 VALUES (NEW.*);
  ELSIF aika >= '2015-04-01'::date AND aika < '2015-07-01'::date THEN 
    INSERT INTO tarkastus_2015_q2 VALUES (NEW.*);
  ELSIF aika >= '2015-07-01'::date AND aika < '2015-10-01'::date THEN 
    INSERT INTO tarkastus_2015_q3 VALUES (NEW.*);
  ELSIF aika >= '2015-10-01'::date AND aika < '2016-01-01'::date THEN 
    INSERT INTO tarkastus_2015_q4 VALUES (NEW.*);
  ELSIF aika >= '2016-01-01'::date AND aika < '2016-04-01'::date THEN 
    INSERT INTO tarkastus_2016_q1 VALUES (NEW.*);
  ELSIF aika >= '2016-04-01'::date AND aika < '2016-07-01'::date THEN 
    INSERT INTO tarkastus_2016_q2 VALUES (NEW.*);
  ELSIF aika >= '2016-07-01'::date AND aika < '2016-10-01'::date THEN 
    INSERT INTO tarkastus_2016_q3 VALUES (NEW.*);
  ELSIF aika >= '2016-10-01'::date AND aika < '2017-01-01'::date THEN 
    INSERT INTO tarkastus_2016_q4 VALUES (NEW.*);
  ELSIF aika >= '2017-01-01'::date AND aika < '2017-04-01'::date THEN 
    INSERT INTO tarkastus_2017_q1 VALUES (NEW.*);
  ELSIF aika >= '2017-04-01'::date AND aika < '2017-07-01'::date THEN 
    INSERT INTO tarkastus_2017_q2 VALUES (NEW.*);
  ELSIF aika >= '2017-07-01'::date AND aika < '2017-10-01'::date THEN 
    INSERT INTO tarkastus_2017_q3 VALUES (NEW.*);
  ELSIF aika >= '2017-10-01'::date AND aika < '2018-01-01'::date THEN 
    INSERT INTO tarkastus_2017_q4 VALUES (NEW.*);
  ELSIF aika >= '2018-01-01'::date AND aika < '2018-04-01'::date THEN 
    INSERT INTO tarkastus_2018_q1 VALUES (NEW.*);
  ELSIF aika >= '2018-04-01'::date AND aika < '2018-07-01'::date THEN 
    INSERT INTO tarkastus_2018_q2 VALUES (NEW.*);
  ELSIF aika >= '2018-07-01'::date AND aika < '2018-10-01'::date THEN 
    INSERT INTO tarkastus_2018_q3 VALUES (NEW.*);
  ELSIF aika >= '2018-10-01'::date AND aika < '2019-01-01'::date THEN 
    INSERT INTO tarkastus_2018_q4 VALUES (NEW.*);
  ELSIF aika >= '2019-01-01'::date AND aika < '2019-04-01'::date THEN 
    INSERT INTO tarkastus_2019_q1 VALUES (NEW.*);
  ELSIF aika >= '2019-04-01'::date AND aika < '2019-07-01'::date THEN 
    INSERT INTO tarkastus_2019_q2 VALUES (NEW.*);
  ELSIF aika >= '2019-07-01'::date AND aika < '2019-10-01'::date THEN 
    INSERT INTO tarkastus_2019_q3 VALUES (NEW.*);
  ELSIF aika >= '2019-10-01'::date AND aika < '2020-01-01'::date THEN 
    INSERT INTO tarkastus_2019_q4 VALUES (NEW.*);
  ELSIF aika >= '2020-01-01'::date AND aika < '2020-04-01'::date THEN 
    INSERT INTO tarkastus_2020_q1 VALUES (NEW.*);
  ELSIF aika >= '2020-04-01'::date AND aika < '2020-07-01'::date THEN 
    INSERT INTO tarkastus_2020_q2 VALUES (NEW.*);
  ELSIF aika >= '2020-07-01'::date AND aika < '2020-10-01'::date THEN 
    INSERT INTO tarkastus_2020_q3 VALUES (NEW.*);
  ELSIF aika >= '2020-10-01'::date AND aika < '2021-01-01'::date THEN 
    INSERT INTO tarkastus_2020_q4 VALUES (NEW.*);  ELSE
    RAISE EXCEPTION 'Taululle tarkastus ei löydy insert ehtoa, korjaa tarkastus_insert() sproc!';
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_tarkastus_insert
BEFORE INSERT ON tarkastus
FOR EACH ROW EXECUTE PROCEDURE tarkastus_insert();


-- Kopioidaan data takaisin
INSERT INTO tarkastus SELECT * FROM tarkastus_temp;
