--  HAR-6038: Reimari-turvalaitekomponentti-tauluun pitää lisätä tila ja viittaus toimenpiteeseen

-- vaihtoehto 1
-- ALTER TABLE reimari_turvalaitekomponentti
--    ADD COLUMN "tila" TEXT,
--    ADD COLUMN "toimenpide" integer,
--    ADD CONSTRAINT toimenpide_fk FOREIGN KEY (toimenpide) REFERENCES reimari_toimenpide (id);

-- vaihtoehto 2

CREATE OR REPLACE VIEW reimari_toimenpiteen_komponenttien_tilamuutokset AS
  SELECT id AS "toimenpide-id",
  "reimari-muokattu" AS muokattu,
  "reimari-luotu" AS luotu,
   (unnest("reimari-komponentit")).tila AS tilakoodi,
   (unnest("reimari-komponentit")).id AS "komponentti-id"
   FROM reimari_toimenpide;

-- vo 3
-- tbd kokeilu: tee funktio tila_ja_tp(k) jota voi käyttää k.tila_ja_tp tyyliin
-- create function koskettu(reimari_toimenpide) RETURNS TIMESTAMP AS $$ SELECT COALESCE($1.muokattu, $1.luotu, $1."reimari-muokattu", $1."reimari-luotu") as t; $$ LANGUAGE sql;

-- CREATE FUNCTION toimenpiteen_komponenttien_tilat(reimari_toimenpide) RETURNS
