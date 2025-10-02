-- Muutostyöt tietomallimuutoksia, erillisrahoitettu muutostyö

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
