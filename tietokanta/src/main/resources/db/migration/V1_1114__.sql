ALTER TABLE tehtavaryhma
    ADD column "voimassaolo_alkuvuosi" INTEGER,
    ADD column "voimassaolo_loppuvuosi" INTEGER;

COMMENT ON COLUMN tehtavaryhma.voimassaolo_alkuvuosi IS E'Vuosi jolloin tehtäväryhmä on otettu käyttöön uusissa urakoissa. Tehtäväryhmää ei oteta käyttöön aiemmin alkaneissa urakoissa.';
COMMENT ON COLUMN tehtavaryhma.voimassaolo_loppuvuosi IS E'Viimeinen vuosi jolloin tehtäväryhmä otetaan käyttöön uusissa urakoissa. Tehtäväryhmä on jatkossakin käytössä tähän mennessä alkaneissa urakoissa.';
