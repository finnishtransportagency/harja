-- TODO: Tämä on paikkausten-hallinta-toteumat aikainen migraatiotiedosto.
--       Muuta versionumero oikeaksi ennen developiin mergeämistä.
--       Siihen asti tehdään kaikki tähän asiaan liittyvät tietokantamuutokset tähän.

ALTER TABLE paikkauskohde
    ADD COLUMN valmistumispvm DATE,
    ADD COLUMN "toteutunut-hinta" NUMERIC;

ALTER TABLE paikkaus DROP CONSTRAINT paikkauksen_uniikki_ulkoinen_id_luoja_urakka;

-- Lisätään käsin lisättäville paikkaustoteumille muutamia kenttiä, joita ei tarvita rajapinnan kautta
-- tulevissa toteumissa

ALTER TABLE paikkaus
    ADD COLUMN massamaara NUMERIC default null,
    ADD COLUMN "pinta-ala" NUMERIC default null;
