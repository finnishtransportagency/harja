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


DROP TRIGGER IF EXISTS paivita_mhu_muutos_kustannusvaikutus_historia_trigger ON mhu_muutos_kustannusvaikutus;
DROP FUNCTION IF EXISTS paivita_mhu_muutos_kustannusvaikutus_historia();
DROP TABLE IF EXISTS mhu_muutos_kustannusvaikutus_historia;
DROP TABLE IF EXISTS mhu_muutos_historia;


CREATE TABLE mhu_muutos_historia (
    LIKE mhu_muutos EXCLUDING CONSTRAINTS EXCLUDING INDEXES
);
CREATE UNIQUE INDEX mhu_muutos_historia_id_versio ON mhu_muutos_historia (id, versio);          -- Varmistetaan että samaa (id, versio)-yhdistelmää on vain yksi
CREATE INDEX mhu_muutos_historia_validi_idx ON mhu_muutos_historia USING GIST (validi_aikana);  -- Tee validi_aikana sarakkeelle indeksi


CREATE TABLE mhu_muutos_kustannusvaikutus_historia(
    LIKE mhu_muutos_kustannusvaikutus EXCLUDING CONSTRAINTS EXCLUDING INDEXES
);
CREATE INDEX muutos_kustannusvaikutus_historia_idx ON mhu_muutos_kustannusvaikutus_historia (muutos, versio, hoitokauden_alkuvuosi);



CREATE OR REPLACE FUNCTION paivita_mhu_muutos_kustannusvaikutus_historia()
  RETURNS TRIGGER AS $$
BEGIN

  IF TG_OP IN ('UPDATE','DELETE') THEN
    INSERT INTO mhu_muutos_kustannusvaikutus_historia
    VALUES (OLD.*);
  END IF;

  IF TG_OP = 'UPDATE' THEN
    NEW.versio := OLD.versio + 1;
    RETURN NEW;
  ELSE
    RETURN OLD;
  END IF;

END;
$$ LANGUAGE plpgsql;


DROP TRIGGER IF EXISTS muutos_kustannusvaikutus_update_trigger ON mhu_muutos_kustannusvaikutus;
DROP TRIGGER IF EXISTS muutos_kustannusvaikutus_delete_trigger ON mhu_muutos_kustannusvaikutus;


CREATE TRIGGER muutos_kustannusvaikutus_update_trigger
  BEFORE UPDATE ON mhu_muutos_kustannusvaikutus
  FOR EACH ROW EXECUTE FUNCTION paivita_mhu_muutos_kustannusvaikutus_historia();


CREATE TRIGGER muutos_kustannusvaikutus_delete_trigger
  BEFORE DELETE ON mhu_muutos_kustannusvaikutus
  FOR EACH ROW EXECUTE FUNCTION paivita_mhu_muutos_kustannusvaikutus_historia();
