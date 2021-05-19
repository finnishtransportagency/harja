-- TODO: Tämä on paikkausten-hallinta-toteumat aikainen migraatiotiedosto.
--       Muuta versionumero oikeaksi ennen developiin mergeämistä.
--       Siihen asti tehdään kaikki tähän asiaan liittyvät tietokantamuutokset tähän.

ALTER TABLE paikkauskohde
    ADD COLUMN valmistumispvm DATE,
    ADD COLUMN tiemerkintapvm timestamp,
    ADD COLUMN "toteutunut-hinta" NUMERIC,
    ADD COLUMN "tiemerkintaa-tuhoutunut?" BOOLEAN,
    ADD COLUMN takuuaika NUMERIC,
    DROP COLUMN nro;


--ALTER TABLE paikkauskohde ADD CONSTRAINT paikkauskohteen_uniikki_ulkoinen_id_luoja_urakka UNIQUE ("ulkoinen-id", "urakka-id", "luoja-id");

-- Lisätään käsin lisättäville paikkaustoteumille muutamia kenttiä, joita ei tarvita rajapinnan kautta
-- tulevissa toteumissa

ALTER TABLE paikkaus
    ADD COLUMN massamaara NUMERIC default null,
    ADD COLUMN "pinta-ala" NUMERIC default null;
