-- name: hae-reikapaikkaukset
SELECT    p.id, 
          p."ulkoinen-id"                AS tunniste,
          (p.tierekisteriosoite).tie     AS tie,
          (p.tierekisteriosoite).aosa    AS aosa,
          (p.tierekisteriosoite).aet     AS aet,
          (p.tierekisteriosoite).losa    AS losa,
          (p.tierekisteriosoite).let     AS let,
          (SELECT nimi FROM paikkauskohde_tyomenetelma WHERE id = p.tyomenetelma) AS "tyomenetelma-nimi",
          p.sijainti,
          p.luotu,
          p."luoja-id",
          p."muokkaaja-id",
          p."reikapaikkaus-yksikko",
          p.tyomenetelma, 
          p.massatyyppi,
          p.alkuaika,
          p.loppuaika,
          p.maara, 
          p.kustannus
FROM      paikkaus p 
WHERE     p."urakka-id" = :urakka-id 
AND       (:tie::TEXT IS NULL OR (p.tierekisteriosoite).tie = :tie)
AND       (:aosa::TEXT IS NULL OR (p.tierekisteriosoite).aosa >= :aosa)
AND       ((:aet::TEXT IS NULL AND :aosa::TEXT IS NULL) OR (:aet::TEXT IS NULL OR (p.tierekisteriosoite).aet >= :aet))
AND       (:losa::TEXT IS NULL OR (p.tierekisteriosoite).losa <= :losa)
AND       ((:let::TEXT IS NULL AND :losa::TEXT IS NULL) OR (:let::TEXT IS NULL OR  (p.tierekisteriosoite).let <= :let))
AND       (:alkuaika::DATE IS NULL OR (p.alkuaika >= :alkuaika::DATE))
AND       (:loppuaika::DATE IS NULL OR (p.loppuaika <= :loppuaika::DATE))
AND       p.poistettu = FALSE
AND       p."paikkaus-tyyppi" = 'reikapaikkaus'
ORDER BY  p.alkuaika DESC;


-- name: luo-tai-paivita-reikapaikkaus!
SELECT reikapaikkaus_upsert( 
    'reikapaikkaus'::paikkaustyyppi,                    -- tyyppi
    :luoja-id::INT,                                     -- luojaid
    NOW()::TIMESTAMP,                                   -- luotu
    COALESCE(:muokkaaja-id::INT, NULL::INT),            -- muokkaaja_id, jos ei olemassa, NULL
    CASE -- Jos muokkaaja ID on läsnä, aseta muokkaus pvm, muuten NULL 
        WHEN :muokkaaja-id IS NOT NULL THEN NOW()::TIMESTAMP
        ELSE NULL::TIMESTAMP
    END,                                                -- muokattu
    NULL::INT,                                          -- poistajaid
    FALSE,                                              -- poistettu
    :urakka-id::INT,                                    -- urakkaid 
    NULL::INT,                                          -- paikkauskohdeid
    :ulkoinen-id::INT,                                  -- ulkoinenid
    COALESCE(:alkuaika::TIMESTAMP, NOW()::TIMESTAMP),   -- alkuaika
    COALESCE(:loppuaika::TIMESTAMP, NOW()::TIMESTAMP),  -- loppuaika, joka on sama, tämä ei taida reikäpaikkauksilla olla relevantti(?)
    ROW(:tie, :aosa, :aet, :losa, :let, NULL)::TR_OSOITE,   -- tr osoite
    COALESCE(:tyomenetelma-id, (SELECT id FROM paikkauskohde_tyomenetelma WHERE nimi = :tyomenetelma))::INT, -- tyomenetelma 
    'Ei määritelty'::TEXT,                              -- massatyyppi, ei määritelty reikäpaikkauksille
    NULL::NUMERIC,                                      -- leveys
    NULL::NUMERIC,                                      -- massamenekki 
    NULL::NUMERIC,                                      -- massamaara 
    NULL::NUMERIC,                                      -- pintaala
    NULL::INTEGER,                                      -- raekoko 
    NULL::TEXT,                                         -- kuulamylly
    :kustannus::NUMERIC,                                -- kustannus
    :yksikko::TEXT,                                     -- yksikkö
    :maara::INT,                                        -- määrä
    (SELECT tierekisteriosoitteelle_viiva(:tie::INT, :aosa::INT, :aet::INT, :losa::INT, :let::INT)) -- last but not least, sijainti geometria
);


-- name: hae-kaikki-tyomenetelmat
SELECT * FROM paikkauskohde_tyomenetelma;


-- name: poista-reikapaikkaustoteuma!
UPDATE  paikkaus 
SET     poistettu = TRUE, 
        "poistaja-id" = :kayttaja-id,
        muokattu = NOW(),
        "muokkaaja-id" = :kayttaja-id -- Halutaanko poistossa asettaa muokkaajan tiedot? 
WHERE   "urakka-id" = :urakka-id 
AND     "ulkoinen-id" = :ulkoinen-id;
