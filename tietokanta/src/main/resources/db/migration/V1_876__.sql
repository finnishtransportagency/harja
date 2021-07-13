-- Paikkauskohde tauluun lisätään paikkauskohteen-tila kenttä, jonka arvot on tässä
create type paikkauskohteen_tila as enum ('ehdotettu', 'hylatty', 'tilattu', 'valmis', 'tarkistettu');

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

-- Paikkauskohde taulussa ei ole ihan kaikkia kenttiä, mitä paikkauskohteen hallinta vaatii.
-- Lisätään puuttuvat kentät
ALTER TABLE paikkauskohde
    ADD COLUMN nro                            TEXT, -- Laskun numero tai muu numero, minkä urakoitsijat voivat paikkauskohteelle antaa.
    ADD COLUMN alkupvm                        DATE, -- Ehdotettu alkupäivä, joka antaa raamit, milloin paikkaus pitäisi aloittaa
    ADD COLUMN loppupvm                       DATE, -- Ehdotettu loppupaiva, joka antaa raamit, milloin paikkaus pitäisi olla valmiina
    ADD COLUMN tilattupvm                     DATE, -- Tilattu paiva, jotta jää merkintä, milloin kohde on tilattu
    ADD COLUMN tyomenetelma                   tyomenetelma, -- esim UREM
    ADD COLUMN tierekisteriosoite_laajennettu tr_osoite_laajennettu, -- tie, alkuetäisyys, alkuosa, loppuetäisyys loppuosa
    ADD COLUMN "paikkauskohteen-tila"         paikkauskohteen_tila, -- ehdotettu, hylätty, tilattu, valmis, tarkistettu
    ADD COLUMN "suunniteltu-maara"            NUMERIC, -- Arvioitu menekki työmenetelmälle
    ADD COLUMN "suunniteltu-hinta"            NUMERIC, -- Paikkauksen arvioitu hinta
    ADD COLUMN yksikko                        TEXT, -- Suunnitellun määrän yksikkö
    ADD COLUMN lisatiedot                     TEXT, -- Paikkauskohteelle voi antaa ehdotusta tehdessä lisätietoja
    ADD COLUMN "pot?"                         BOOLEAN DEFAULT FALSE, -- Muutamalla työmenetelmällä voi valita, että toteutukset menevätkin pot lomakkeen kautta
    ALTER COLUMN "ulkoinen-id" DROP NOT NULL; -- Poistetaan rajoitus, koska nyt kohteet eivät enää tule aina ulkoa.

ALTER TABLE paikkauskohde
    RENAME COLUMN tila TO "yhalahetyksen-tila";

-- Singletonilta tarkastettuna, työmenetelmiä on paikkaus ja paikkaustoteuma-tauluissa.
-- Paikkaus-kannassa on eri tavalla merkattuja UREM-paikkauksia. Muutetaan ne yhtenäiseen muotoon, jotta voidaan muuttaa enumiksi.
-- Molemmissa on testikohteisiin kohdistettuja massapintauksia. Nämä voidaan korvata AB-paikkaus levittäjällä-työmenetelmällä.
-- Stagingilla on kuumennuspintaus, SREM ja paksuudeltaan vakio laatta - tyyppisiä työmenetelmiä. Näistä ID:t tallessa tarvittaessa.

UPDATE paikkaus
    SET tyomenetelma = 'UREM'
WHERE tyomenetelma IN ('RREM', 'urapaikkaus', 'uraremix', 'urapaikkaus');

UPDATE paikkaus
    SET tyomenetelma = 'AB-paikkaus levittäjällä'
WHERE tyomenetelma IN ('massapintaus', 'kuumennuspintaus');

UPDATE paikkaus
SET tyomenetelma = 'SMA-paikkaus levittäjällä'
WHERE tyomenetelma = 'SREM';

UPDATE paikkaus
SET tyomenetelma = 'Muu päällysteiden paikkaustyö'
WHERE tyomenetelma = 'paksuudeltaan vakio laatta';

UPDATE paikkaustoteuma
    SET tyomenetelma = 'AB-paikkaus levittäjällä'
WHERE tyomenetelma = 'massapintaus';

ALTER TABLE paikkaustoteuma
    ALTER COLUMN tyomenetelma TYPE tyomenetelma USING tyomenetelma::tyomenetelma;

ALTER TABLE paikkaus
    ALTER COLUMN tyomenetelma TYPE tyomenetelma USING tyomenetelma::tyomenetelma;
