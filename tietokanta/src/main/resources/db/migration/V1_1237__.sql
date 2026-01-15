-- Lisätään elinvoimakeskukistakin numerotieto
-- Tallennetaan eri sarakkeeseen kuin elynumero, että ei mene sekaisin
ALTER TABLE organisaatio
ADD COLUMN elinvoimakeskusnumero INTEGER;
