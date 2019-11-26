-- Toimenpidekoodi ja tehtavaryhma tauluihin tarvitaan tunniste, joka on
-- joka kannassa sama eikä muutu ikinä.
ALTER TABLE toimenpidekoodi ADD COLUMN tunniste UUID UNIQUE;
ALTER TABLE tehtavaryhma ADD COLUMN tunniste UUID UNIQUE;