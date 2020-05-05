ALTER TABLE toimenpidekoodi
ADD COLUMN suunnitteluyksikko TEXT;

COMMENT ON COLUMN toimenpidekoodi.yksikko IS 'Tehtävään liittyvä yksikkö. Näkyy toteumatietojen yhteydessä, kun toteutunut määrä näytetään. Esim. suolauskilometrit.';
COMMENT ON COLUMN toimenpidekoodi.suunnitteluyksikko IS 'Tehtävään tai tehtävämateriaaliin liittyvä yksikkö. Näkyy tehtävä- ja materiaalinäkymässä. Yksikkö jonka määriä suunnitellaan. Esim. suolan määrä. Suolauskilometrien määrää ei suunnitella.';
