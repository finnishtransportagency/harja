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
    ADD COLUMN "pinta-ala" NUMERIC default null,
    ADD COLUMN lahde lahde default 'harja-api'::lahde;


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

-- Päivitetään "vanhoille" paikkauskohteille myös alkupvm ja loppupvm paikkausten perusteella, jotta saadaan ne näkyviin
-- paikkauskohde listaukseen
UPDATE paikkauskohde pk
SET alkupvm = (SELECT p.alkuaika
                    FROM paikkaus p
                    WHERE p."paikkauskohde-id" = pk.id),
    loppupvm =  (SELECT MAX(p.loppuaika)
                 FROM paikkaus p
                 WHERE p."paikkauskohde-id" = pk.id)
WHERE pk.tyomenetelma IS NULL;

-- Otetaan paikkauksilta pois vaatius, että aina on oltava ulkoinen id. Käsin lisätyillä paikkauksilla sitä ei voi olla
ALTER TABLE paikkaus DROP CONSTRAINT paikkauksen_uniikki_ulkoinen_id_luoja_urakka;