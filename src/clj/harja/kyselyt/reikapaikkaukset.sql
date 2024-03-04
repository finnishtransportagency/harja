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
    :luoja_id,                         -- luojaid
    NOW()::TIMESTAMP,     --luotu
    NULL::INT,          --muokkaajaid
    NULL::TIMESTAMP,      --muokattu
    NULL::INT,      --poistajaid
    FALSE,      -- poistettu
    :urakka_id,       -- urakkaid 
    NULL::INT,      --paikkauskohdeid
    :ulkoinen_id,     -- ulkoinenid
    NOW()::TIMESTAMP,     --alkuaika
    NOW()::TIMESTAMP,     -- loppuaika, laitetaan vaan sama, nämä ei reikäpaikkauksilla ole niin relevantteja(?)
    ROW(:tie, :aosa, :aet, :losa, :let, NULL)::TR_OSOITE,   -- tr osoite
    (SELECT id FROM paikkauskohde_tyomenetelma WHERE nimi = :tyomenetelma),   -- tyomenetelma
    'AB, Asfalttibetoni'::TEXT,     -- massatyyppi, ei tietoa miten tämä reikäpaikkauksille, laitettu AB, failaa muuten NOT NULL constraint
    NULL::NUMERIC,  -- leveys
    NULL::NUMERIC,   -- massamenekki 
    :massamaara::NUMERIC,  -- massamaara 
    NULL::NUMERIC,  -- pintaala
    NULL::INTEGER,     -- raekoko 
    NULL::TEXT,  -- kuulamylly
    :kustannus::NUMERIC, -- kustannus
    :yksikko::TEXT, -- yksikkö
    (SELECT tierekisteriosoitteelle_viiva(:tie, :aosa, :aet, :losa, :let)) -- last but not least, sijainti geometria
);
