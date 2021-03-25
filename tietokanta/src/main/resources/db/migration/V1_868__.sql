CREATE TYPE tyomenetelma AS ENUM (
    --'AB-paikkaus levittäjällä',
    'ABPL',
    --'PAB-paikkaus levittäjällä',
    'PABPL',
    -- SMA-paikkaus levittäjällä
    'SMA',
    --'KT-valuasfalttipaikkaus (KTVA)',
    'KTVA',
    --'Konetiivistetty reikävaluasfalttipaikkaus (REPA)',
    'REPA',
    --'Sirotepuhalluspaikkaus (SIPU)',
    'SIPU',
    --'Sirotepintauksena tehty lappupaikkaus (SIPA)',
    'SIPA',
    --'Urapaikkaus (UREM/RREM)',
    'UREM',
    -- Jyrsintä (HJYR/TJYR)
    'HJYR',
    --'Kannukaatosaumaus',
    'KKSA',
    --'KT-valuasfalttisaumaus',
    'KTVASA',
    --'Avarrussaumaus',
    'AVSA',
    -- "Sillan kannen päällysteen päätysauman korjaukset"
    'SKPPK',
    --'Reunapalkin ja päällysteen välisen sauman tiivistäminen',
    'RPVST',
    --'Reunapalkin liikuntasauman tiivistäminen',
    'RLSAT',
    --'Käsin tehtävät paikkaukset pikapaikkausmassalla',
    'KTPP',
    --'AB-paikkaus käsin',
    'ABPK',
    --'PAB-paikkaus käsin',
    'PABPK',
    --'Muu päällysteiden paikkaustyö'
    'MPA');

UPDATE paikkauskohde
SET tyomenetelma = 'UREM'
WHERE harja.public.paikkauskohde.tyomenetelma IN ('RREM', 'urapaikkaus');

ALTER TABLE paikkauskohde
    ALTER COLUMN tyomenetelma TYPE tyomenetelma USING tyomenetelma::tyomenetelma;

ALTER TABLE paikkaustoteuma
    ALTER COLUMN tyomenetelma TYPE tyomenetelma USING tyomenetelma::tyomenetelma;

ALTER TABLE paikkaus
    ALTER COLUMN tyomenetelma TYPE tyomenetelma USING tyomenetelma::tyomenetelma;
