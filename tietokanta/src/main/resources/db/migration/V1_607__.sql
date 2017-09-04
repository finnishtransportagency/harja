--  HAR-6038: Reimari-turvalaitekomponentti-tauluun pitää lisätä tila ja viittaus toimenpiteeseen

CREATE OR REPLACE VIEW reimari_toimenpiteen_komponenttien_tilamuutokset AS
  SELECT id AS "toimenpide-id",
  "reimari-muokattu" AS muokattu,
  "reimari-luotu" AS luotu,
   (unnest("reimari-komponentit")).tila AS tilakoodi,
   (unnest("reimari-komponentit")).id AS "komponentti-id"
   FROM reimari_toimenpide;
