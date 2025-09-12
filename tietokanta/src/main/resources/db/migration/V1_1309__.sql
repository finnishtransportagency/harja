ALTER TYPE MHU_MUUTOSTYYPPI ADD VALUE 'muutostyo' AFTER 'maarapoikkeama';


-- Lomaketyyppi 'muutostyo' 
-- halutaan 2 eri tyyppistä muutostyötä, sille uusi columni
--
-- Mahdolliset tyypit:
--    Erillisrahoituksella tehtävä muutostyö
--    Poikkeaminen tehtävä- ja määräluettelon määrästä
CREATE TYPE MHU_MUUTOS_ALITYYPPI AS ENUM (
  'erillisrahoitus',
  'poikkeama'
);

ALTER TABLE mhu_muutos ADD COLUMN alityyppi MHU_MUUTOS_ALITYYPPI DEFAULT NULL;


ALTER TABLE mhu_muutos_kustannusvaikutus ADD CONSTRAINT uniikki_muutos_kustannusvaikutus UNIQUE (muutos, hoitokauden_alkuvuosi);
DROP INDEX IF EXISTS mhu_muutos_kustannusvaikutus_idx;
CREATE INDEX mhu_muutos_kustannusvaikutus_mvt_idx ON mhu_muutos_kustannusvaikutus (muutos, versio, toimenpideinstanssi);
