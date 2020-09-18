ALTER TABLE toimenpidekoodi
ADD column "voimassaolo_alkuvuosi" INTEGER,
ADD column "voimassaolo_loppuvuosi" INTEGER;

COMMENT ON COLUMN toimenpidekoodi.voimassaolo_alkuvuosi IS E'Vuosi jolloin tehtävä on otettu käyttöön uusissa urakoissa. Tehtävää ei oteta käyttöön aiemmin alkaneissa urakoissa.';
COMMENT ON COLUMN toimenpidekoodi.voimassaolo_loppuvuosi IS E'Viimeinen vuosi jolloin tehtävä otetaan käyttöön uusissa urakoissa. Tehtävä on jatkossakin käytössä tähän mennessä alkaneissa urakoissa.';

