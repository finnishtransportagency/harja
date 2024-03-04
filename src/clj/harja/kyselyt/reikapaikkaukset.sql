-- name: hae-reikapaikkaukset
SELECT 
    p.id, 
    p.sijainti,
    (p.tierekisteriosoite).tie     AS tie,
    (p.tierekisteriosoite).aosa    AS aosa,
    (p.tierekisteriosoite).aet     AS aet,
    (p.tierekisteriosoite).losa    AS losa,
    (p.tierekisteriosoite).let     AS let,
    (SELECT nimi FROM paikkauskohde_tyomenetelma WHERE id = p.tyomenetelma) AS "tyomenetelma", 
    p.massatyyppi,
    p.alkuaika
FROM paikkaus p WHERE p."urakka-id" = :urakka-id LIMIT 20;
