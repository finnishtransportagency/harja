--  HAR-6038: Reimari-turvalaitekomponentti-tauluun pitää lisätä tila ja viittaus toimenpiteeseen

-- vaihtoehto 1
ALTER TABLE reimari_turvalaitekomponentti
   ADD COLUMN "tila" TEXT,
   ADD COLUMN "toimenpide" integer,
   ADD CONSTRAINT toimenpide_fk FOREIGN KEY (toimenpide) REFERENCES reimari_toimenpide (id);

-- vaihtoehto 2

CREATE VIEW komponenttien_tilamuutokset as select id AS tp_id, "reimari-muokattu" AS muokattu, "reimari-luotu" AS luotu, (unnest("reimari-komponentit")).tila AS komp_tila, (unnest("reimari-komponentit")).id AS komp_id FROM reimari_toimenpide;

-- vo 3
-- tbd kokeilu: tee funktio tila_ja_tp(k) jota voi käyttää k.tila_ja_tp tyyliin
