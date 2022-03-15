-- Kahden TR-osoitteen leikkaus
--
-- Funktio, joka kertoo leikkaavatko kaksi TR-osoitetta. TR-osoitteiden ei tarvitse olla todellisia.
--
-- Merkinnät
-- a ja b ovat intervalleja joissa a1 on a:n alku, a2 on a:n loppu sekä b1 b:n alku ja b2 b:n loppu.
--
-- ----a1======a2-----------------
-- -----------------b1=======b2---
--
-- Lahtöoletus: a1 < a2 ja b1 < b2, eli intervallien alku ja loppu ovat järjestyksessä
--
-- Predikaatti P0: a ja b eivät leikkaa
-- Suomeksi: "b on kokonaan a:n jälkeen TAI b on kokonaan ennen a:ta"
-- ----b1====b2---a1====a2-----
-- --------a1====a2---b1====b2---
-- a1 > b2 tai a2 < b1 => alueet eivät leikkaa
--
-- Predikaatti P1: a ja b leikkaavat
-- Tämä on komplementti sen kanssa, että a ja b eivät leikkaa.
-- P1 = ! P0
--
--               !(a1 > b2 tai a2 < b1)
-- DeMorgan <=>  !(a1 > b2) ja !(a2 < b1)
--          <=>  (a1 <= b2) ja (a2 => b1)
--
-- Suomeksi: "a:n alku on ennen b:n loppua JA a:n loppu on ennen b:n alkua".
--

-- Vertaile vertaa kahden hierakiatason intervalleja
-- Ensimmäinen taso on osat ja toinen taso etäisyydet osan sisällä
-- Toisen tason vertalua tarvitaan vain jos osat ovat samat.
CREATE FUNCTION tr_vertaile(aosa int, aeta int, bosa int, beta int)
    RETURNS int
    LANGUAGE plpgsql
AS
$$
DECLARE
BEGIN
    IF aosa = bosa THEN
        RETURN SIGN(aeta - beta); -- toinen taso
    ELSE
        RETURN SIGN(aosa - bosa); -- ensimmäinen taso
    END IF;
END;
$$;

-- Funktio testaa ovatko kaksi TR osoitetta leikkauksessa.
-- Jos vain tie2 on annettu, ei vertailla osia ja etaisyyksiä
-- Jos losa* ei ole annettu, oletetaan pistemäinen vertailu
CREATE FUNCTION varuste_leikkaus(tie1 int, aosa1 int, aeta1 int, losa1 int, leta1 int,
                                 tie2 int, aosa2 int, aeta2 int, losa2 int, leta2 int)
    RETURNS boolean
    LANGUAGE plpgsql
AS
$$
DECLARE
    losa1_ei_null int;
    leta1_ei_null int;
    losa2_ei_null int;
    leta2_ei_null int;
BEGIN
    IF losa1 IS NULL THEN
        losa1_ei_null = aosa1;
        leta1_ei_null = aeta1;
    ELSE
        losa1_ei_null = losa1;
        leta1_ei_null = leta1;
    END IF;

    IF losa2 IS NULL THEN
        losa2_ei_null = aosa2;
        leta2_ei_null = aeta2;
    ELSE
        losa2_ei_null = losa2;
        leta2_ei_null = leta2;
    END IF;

    IF tie2 IS NULL THEN
        RETURN TRUE;
    ELSIF tie1 <> tie2 THEN
        RETURN FALSE;
    ELSIF aosa2 IS NULL THEN
        -- CASE 1: filtterissä vain tie (ja se on sama)
        RETURN TRUE;
    ELSE
        -- Predikaatti P1: a (data) ja b (filter) leikkaavat, (a ja b ovat osa ja etaisyys tupleja)
        -- 1 = alku , 2 = loppu
        -- (a1 <= b2) ja (a2 => b1)
        RETURN (tr_vertaile(aosa1, aeta1, losa2_ei_null, leta2_ei_null) <= 0
            AND tr_vertaile(losa1_ei_null, leta1_ei_null, aosa2, aeta2) >= 0);
    END IF;
END;
$$;
