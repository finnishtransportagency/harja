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

CREATE TABLE paikkauskohde_tyomenetelma
(
    id      SERIAL PRIMARY KEY,
    nimi    TEXT,
    lyhenne TEXT
);
INSERT INTO paikkauskohde_tyomenetelma (nimi, lyhenne)
VALUES ('AB-paikkaus levittäjällä', null),
       ('PAB-paikkaus levittäjällä', null),
       ('SMA-paikkaus levittäjällä', null),
       ('KT-valuasfalttipaikkaus (KTVA)', 'KTVA'),
       ('Konetiivistetty reikävaluasfalttipaikkaus (REPA)', 'REPA'),
       ('Sirotepuhalluspaikkaus (SIPU)', 'SIPU'),
       ('Sirotepintauksena tehty lappupaikkaus (SIPA)', 'SIPA'),
       ('Urapaikkaus (UREM/RREM)', 'UREM'),
       ('Jyrsintäkorjaukset (HJYR/TJYR)', 'HJYR'),
       ('Kannukaatosaumaus', null),
       ('Avarrussaumaus', null),
       ('Sillan kannen päällysteen päätysauman korjaukset', null),
       ('Reunapalkin ja päällysteen välisen sauman tiivistäminen', null),
       ('Reunapalkin liikuntasauman tiivistäminen', null),
       ('Käsin tehtävät paikkaukset pikapaikkausmassalla', null),
       ('AB-paikkaus käsin', null),
       ('PAB-paikkaus käsin', null),
       ('Muu päällysteiden paikkaustyö', null);

CREATE FUNCTION tyomenetelma_to_id(tm tyomenetelma)
    RETURNS INTEGER AS
$$
DECLARE
    retval INTEGER;
BEGIN
    SELECT id INTO retval FROM paikkauskohde_tyomenetelma ptm WHERE ptm.nimi = tm::TEXT OR ptm.lyhenne = tm::text;
    RETURN retval;
END
$$ LANGUAGE plpgsql;

ALTER TABLE paikkauskohde
    ALTER COLUMN tyomenetelma TYPE INTEGER USING tyomenetelma_to_id(tyomenetelma),
    ADD CONSTRAINT fk_tyomenetelma_paikkauskohde FOREIGN KEY (tyomenetelma) REFERENCES paikkauskohde_tyomenetelma (id);

ALTER TABLE paikkaus
    ALTER COLUMN tyomenetelma TYPE INTEGER USING tyomenetelma_to_id(tyomenetelma),
    ADD CONSTRAINT fk_tyomenetelma_paikkaus FOREIGN KEY (tyomenetelma) REFERENCES paikkauskohde_tyomenetelma (id);

ALTER TABLE paikkaustoteuma
    ALTER COLUMN tyomenetelma TYPE INTEGER USING tyomenetelma_to_id(tyomenetelma),
    ADD CONSTRAINT fk_tyomenetelma_paikkaustoteuma FOREIGN KEY (tyomenetelma) REFERENCES paikkauskohde_tyomenetelma (id);

DROP FUNCTION tyomenetelma_to_id(tm tyomenetelma);
DROP TYPE tyomenetelma;
