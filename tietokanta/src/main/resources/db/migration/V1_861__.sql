-- Paikkauskohde tauluun lisätään paikkauskohteen-tila kenttä, jonka arvot on tässä
create type paikkauskohteen_tila as enum ('ehdotettu', 'hylatty', 'tilattu', 'valmis', 'tarkistettu');

-- Paikkauskohde taulussa ei ole ihan kaikkiakenttiä, mitä paikkauskohteen hallinta vaatii.
-- Lisätään puuttuvat kentät
ALTER TABLE paikkauskohde
    ADD COLUMN alkuaika timestamp,
    ADD COLUMN loppuaika timestamp,
    ADD COLUMN tyomenetelma TEXT,
    ADD COLUMN tyomenetelma_kuvaus TEXT,
    ADD COLUMN tierekisteriosoite tr_osoite,
    ADD COLUMN "paikkauskohteen-tila" paikkauskohteen_tila;

ALTER TABLE paikkauskohde
    RENAME COLUMN tila TO "yhalahetyksen-tila";

