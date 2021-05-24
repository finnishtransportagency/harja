-- Lisätään paikkauskohdetauluun tarvittavat uudet kolumnit, jotta voidaan toteuttaa uuden suunnitelman mukaiset
-- toiminnot.

ALTER TABLE paikkauskohde
    ADD COLUMN valmistumispvm DATE,
    ADD COLUMN tiemerkintapvm timestamp,
    ADD COLUMN "toteutunut-hinta" NUMERIC,
    ADD COLUMN "tiemerkintaa-tuhoutunut?" BOOLEAN,
    ADD COLUMN takuuaika NUMERIC,
    DROP COLUMN nro;

-- Lisätään käsin lisättäville paikkaustoteumille muutamia kenttiä, joita ei tarvita rajapinnan kautta
-- tulevissa toteumissa

ALTER TABLE paikkaus
    ADD COLUMN massamaara NUMERIC default null,
    ADD COLUMN "pinta-ala" NUMERIC default null;


-- Päivitetään "vanhoille" paikkauskohteille työmenetelmät paikkaustoteumien perusteella
UPDATE paikkauskohde pk
   SET tyomenetelma = (SELECT p.tyomenetelma
                         FROM paikkaus p
                        WHERE p."paikkauskohde-id" = pk.id
                          AND p.tyomenetelma IS NOT NULL
                        ORDER BY p.id DESC
                        LIMIT 1)
WHERE pk.tyomenetelma IS NULL;

-- Päivitetään "vanhoille" paikkauskohteille paikkauskohde-tila -> valmis, jotta niitäkin voidaan tarkistella
-- paikkauskohdelistauksessa
UPDATE paikkauskohde pk
   SET "paikkauskohteen-tila" = 'valmis'
 WHERE pk."paikkauskohteen-tila" IS NULL;