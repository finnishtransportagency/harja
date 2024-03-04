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

-- name: luo-tai-paivita-reikapaikkaus!
SELECT reikapaikkaus_upsert(
    'reikapaikkaus'::paikkaustyyppi, -- tyyppi
    3,                         -- luojaid
    NOW()::TIMESTAMP,     --luotu
    NULL::INT,          --muokkaajaid
    NULL::TIMESTAMP,      --muokattu
    NULL::INT,      --poistajaid
    FALSE,      -- poistettu
    14,       -- urakkaid 
    NULL::INT,      --paikkauskohdeid
    123452,     -- ulkoinenid
    NOW()::TIMESTAMP,     --alkuaika
    (NOW() + INTERVAL '20 day')::TIMESTAMP,     -- loppuaika 
    ROW(20, 19, (50 + 3), 19, (51 + 3), NULL)::TR_OSOITE,   -- tr osoite
    (SELECT id FROM paikkauskohde_tyomenetelma WHERE lyhenne = 'UREM'),   -- tyomenetelma
    'AB, Asfalttibetoni'::TEXT,     -- massatyyppi 
    1.2,  -- leveys
    23.0,   -- massamenekki 
    0.0276,  -- massamaara 
    1.2,  -- pintaala
    5,     -- raekoko 
    'AN7'::TEXT,  -- kuulamylly
    200.00, -- kustannus
    (SELECT tierekisteriosoitteelle_viiva(20, 19, (50 + 3), 19, (51 + 3))) -- last but not least, sijainti geometria
);
