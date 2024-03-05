-- name: hae-reikapaikkaukset
SELECT 
    p.id, 
    p."ulkoinen-id"                AS tunniste,
    (p.tierekisteriosoite).tie     AS tie,
    (p.tierekisteriosoite).aosa    AS aosa,
    (p.tierekisteriosoite).aet     AS aet,
    (p.tierekisteriosoite).losa    AS losa,
    (p.tierekisteriosoite).let     AS let,
    (SELECT nimi FROM paikkauskohde_tyomenetelma WHERE id = p.tyomenetelma) AS "tyomenetelma-nimi",
    p.sijainti,
    p.yksikko,
    p.tyomenetelma, 
    p.massatyyppi,
    p.alkuaika,
    p.paikkaus_maara, 
    p.kustannus
FROM paikkaus p 
WHERE p."urakka-id" = :urakka-id 
AND p.tyyppi = 'reikapaikkaus';


-- name: luo-tai-paivita-reikapaikkaus!
SELECT reikapaikkaus_upsert( 
    'reikapaikkaus'::paikkaustyyppi, -- tyyppi
    :luoja_id::INT,                         -- luojaid
    NOW()::TIMESTAMP,     --luotu
    NULL::INT,          --muokkaajaid
    NULL::TIMESTAMP,      --muokattu
    NULL::INT,      --poistajaid
    FALSE,      -- poistettu
    :urakka_id::INT,       -- urakkaid 
    NULL::INT,      --paikkauskohdeid
    :ulkoinen_id::INT,     -- ulkoinenid
    NOW()::TIMESTAMP,     --alkuaika
    NOW()::TIMESTAMP,     -- loppuaika, laitetaan vaan sama, nämä ei reikäpaikkauksilla ole niin relevantteja(?)
    ROW(:tie, :aosa, :aet, :losa, :let, NULL)::TR_OSOITE,   -- tr osoite
    COALESCE(:tyomenetelma-id, (SELECT id FROM paikkauskohde_tyomenetelma WHERE nimi = :tyomenetelma))::INT, -- tyomenetelma
    'AB, Asfalttibetoni'::TEXT,     -- massatyyppi, ei tietoa miten tämä reikäpaikkauksille, laitettu AB, failaa muuten NOT NULL constraint
    NULL::NUMERIC,  -- leveys
    NULL::NUMERIC,   -- massamenekki 
    NULL::NUMERIC,  -- massamaara 
    NULL::NUMERIC,  -- pintaala
    NULL::INTEGER,     -- raekoko 
    NULL::TEXT,  -- kuulamylly
    :kustannus::NUMERIC, -- kustannus
    :yksikko::TEXT, -- yksikkö
    :paikkaus_maara::INT, -- paikkaus_maara
    (SELECT tierekisteriosoitteelle_viiva(:tie::INT, :aosa::INT, :aet::INT, :losa::INT, :let::INT)) -- last but not least, sijainti geometria
);


-- name: hae-kaikki-tyomenetelmat
SELECT * FROM paikkauskohde_tyomenetelma;
