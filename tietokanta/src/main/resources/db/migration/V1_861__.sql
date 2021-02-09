-- Paikkauskohde tauluun lisätään paikkauskohteen-tila kenttä, jonka arvot on tässä
create type paikkauskohteen_tila as enum ('ehdotettu', 'hylatty', 'tilattu', 'valmis', 'tarkistettu');

-- Paikkauskohde taulussa ei ole ihan kaikkiakenttiä, mitä paikkauskohteen hallinta vaatii.
-- Lisätään puuttuvat kentät
ALTER TABLE paikkauskohde
    ADD COLUMN nro TEXT, -- Laskun numero tai muu numero, minkä urakoitsijat voivat paikkauskohteelle antaa.
    ADD COLUMN alkuaika timestamp, -- Ehdotettu alkuaika, joka antaa raamit, milloin paikkaus pitäisi aloittaa
    ADD COLUMN loppuaika timestamp, -- Ehdotettu loppuaika, joka antaa raamit, milloin paikkaus pitäisi olla valmiina
    ADD COLUMN tyomenetelma TEXT, -- esim UREM
    ADD COLUMN tyomenetelma_kuvaus TEXT, -- Vapaa kuvaus mitä työmenetelmää käytetään
    ADD COLUMN tierekisteriosoite tr_osoite, -- tie, alkuetäisyys, alkuosa, loppuetäisyys loppuosa
    ADD COLUMN "paikkauskohteen-tila" paikkauskohteen_tila; -- ehdotettu, hylätty, tilattu, valmis, tarkistettu

ALTER TABLE paikkauskohde
    RENAME COLUMN tila TO "yhalahetyksen-tila";

