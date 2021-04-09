-- Paikkauskohde tauluun lisätään paikkauskohteen-tila kenttä, jonka arvot on tässä
create type paikkauskohteen_tila as enum ('ehdotettu', 'hylatty', 'tilattu', 'valmis', 'tarkistettu');

-- Paikkauskohde taulussa ei ole ihan kaikkiakenttiä, mitä paikkauskohteen hallinta vaatii.
-- Lisätään puuttuvat kentät
ALTER TABLE paikkauskohde
    ADD COLUMN nro TEXT, -- Laskun numero tai muu numero, minkä urakoitsijat voivat paikkauskohteelle antaa.
    ADD COLUMN alkupvm DATE, -- Ehdotettu alkupäivä, joka antaa raamit, milloin paikkaus pitäisi aloittaa
    ADD COLUMN loppupvm DATE, -- Ehdotettu loppupaiva, joka antaa raamit, milloin paikkaus pitäisi olla valmiina
    ADD COLUMN tilattupvm DATE, -- Tilattu paiva, jotta jää merkintä, milloin kohde on tilattu
    ADD COLUMN tyomenetelma TEXT, -- esim UREM
    ADD COLUMN tierekisteriosoite_laajennettu tr_osoite_laajennettu, -- tie, alkuetäisyys, alkuosa, loppuetäisyys loppuosa
    ADD COLUMN "paikkauskohteen-tila" paikkauskohteen_tila, -- ehdotettu, hylätty, tilattu, valmis, tarkistettu
    ADD COLUMN "suunniteltu-maara" NUMERIC, -- Arvioitu menekki työmenetelmälle
    ADD COLUMN "suunniteltu-hinta" NUMERIC, -- Paikkauksen arvioitu hinta
    ADD COLUMN yksikko TEXT, -- Suunnitellun määrän yksikkö
    ADD COLUMN lisatiedot TEXT, -- Paikkauskohteelle voi antaa ehdotusta tehdessä lisätietoja
    ALTER COLUMN "ulkoinen-id" DROP NOT NULL; -- Poistetaan rajoitus, koska nyt kohteet eivät enää tule aina ulkoa.

ALTER TABLE paikkauskohde
    RENAME COLUMN tila TO "yhalahetyksen-tila";

CREATE TYPE tyomenetelma AS ENUM (
    'AB-paikkaus levittäjällä',
    'PAB-paikkaus levittäjällä',
    'SMA-paikkaus levittäjällä',
    'KTVA', --'KT-valuasfalttipaikkaus (KTVA)',
    'REPA', --'Konetiivistetty reikävaluasfalttipaikkaus (REPA)',
    'SIPU', --'Sirotepuhalluspaikkaus (SIPU)',
    'SIPA', --'Sirotepintauksena tehty lappupaikkaus (SIPA)',
    'UREM', --'Urapaikkaus (UREM/RREM)',
    'HJYR', -- Jyrsintä (HJYR/TJYR)
    'Kannukaatosaumaus',
    'KT-valuasfalttisaumaus',
    'Avarrussaumaus',
    'Sillan kannen päällysteen päätysauman korjaukset',
    'Reunapalkin ja päällysteen välisen sauman tiivistäminen',
    'Reunapalkin liikuntasauman tiivistäminen',
    'Käsin tehtävät paikkaukset pikapaikkausmassalla',
    'AB-paikkaus käsin',
    'PAB-paikkaus käsin',
    'Muu päällysteiden paikkaustyö');

UPDATE paikkauskohde
SET tyomenetelma = 'UREM'
WHERE harja.public.paikkauskohde.tyomenetelma IN ('RREM', 'urapaikkaus');

ALTER TABLE paikkauskohde
    ALTER COLUMN tyomenetelma TYPE tyomenetelma USING tyomenetelma::tyomenetelma;

ALTER TABLE paikkaustoteuma
    ALTER COLUMN tyomenetelma TYPE tyomenetelma USING tyomenetelma::tyomenetelma;

ALTER TABLE paikkaus
    ALTER COLUMN tyomenetelma TYPE tyomenetelma USING tyomenetelma::tyomenetelma;
