-- Muutostyöt tietomallimuutoksia, erillisrahoitettu muutostyö

ALTER TYPE kohdistustyyppi ADD VALUE 'erillisrahoitettu-muutos';
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


-- Poista ON DELETE, jätä pelkkä ON UPDATE 
DROP TRIGGER IF EXISTS mhu_muutos_kulu_historia_trigger ON mhu_muutos_kulu;

CREATE TRIGGER mhu_muutos_kulu_historia_trigger
    BEFORE UPDATE ON mhu_muutos_kulu
    FOR EACH ROW
EXECUTE FUNCTION paivita_mhu_muutos_kulu_historia();
