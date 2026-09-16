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
          p.kustannus,
          p.pkluokka,
          p.luokittelu
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


-- name: lisaa-reikapaikkaus!
INSERT INTO paikkaus (
  "paikkaus-tyyppi", 
  "luoja-id", 
  luotu, 
  "muokkaaja-id", 
  muokattu, 
  "poistaja-id", 
  poistettu, 
  "urakka-id", 
  "paikkauskohde-id", 
  "ulkoinen-id", 
  alkuaika, 
  loppuaika, 
  tierekisteriosoite, 
  tyomenetelma, 
  massatyyppi, 
  leveys,
  lahde,
  massamenekki, 
  massamaara, 
  "pinta-ala", 
  raekoko, 
  kuulamylly, 
  kustannus,
  "reikapaikkaus-yksikko",
  maara,
  luokittelu,
  sijainti
)
VALUES (
  'reikapaikkaus',                -- "paikkaus-tyyppi", aina reikäpaikkaus
  :luoja-id,                      -- luoja
  NOW()::TIMESTAMP,               -- luotu
  COALESCE(:muokkaaja-id::INT, NULL::INT),  -- "muokkaaja-id"
  NULL::TIMESTAMP,                -- muokattu
  NULL::INT,                      -- "poistaja-id"
  FALSE,                          -- poistettu
  :urakka-id::INT,                -- "urakka-id"
  NULL::INT,                      -- "paikkauskohde-id", reikäpaikkauksilla ei ole paikkauskohdetta
  :ulkoinen-id,                   -- "ulkoinen-id"
  COALESCE(:alkuaika::TIMESTAMP, NOW()::TIMESTAMP),     -- alkuaika
  COALESCE(:loppuaika::TIMESTAMP, NOW()::TIMESTAMP),    -- loppuaika
  ROW(:tie, :aosa, :aet, :losa, :let, NULL)::TR_OSOITE, -- tierekisteriosoite
  COALESCE(:tyomenetelma-id, (SELECT id FROM paikkauskohde_tyomenetelma WHERE nimi = :tyomenetelma)), -- tyomenetelma 
  'Ei määritelty', -- massatyyppi, 'Ei määritelty' reikäpaikkauksille
  NULL::NUMERIC, -- leveys
  :lahde::lahde, -- harja-api / harja-ui
  NULL::NUMERIC, -- massamenekki
  NULL::NUMERIC, -- massamaara
  NULL::NUMERIC, -- "pinta-ala"
  NULL::INTEGER, -- raekoko
  NULL::TEXT, -- kuulamylly
  :kustannus::NUMERIC, -- kustannus
  :yksikko::TEXT, -- "reikapaikkaus-yksikko"
  :maara::INT, -- maara
  :luokittelu::TEXT[], -- paikkauksen kategoisoinnit
  (SELECT tieosoitteelle_geometria(:tie::INT, :aosa::INT, :aet::INT, :losa::INT, :let::INT)) -- sijainti geometria
);

-- name: lisaa-tai-paivita-reikapaikkauksen-tienkohta!
-- Reikäpaikkauksessa voi olla vain yksi tienkohta. Älä käytä samaa logiikkaa urapaikkauksissa.
INSERT INTO paikkauksen_tienkohta ("paikkaus-id", ajorata, kaista, paikkaustyyppi, "kasin-maaritelty")
VALUES (:paikkaus-id, :ajorata, :kaista, 'reikapaikkaus'::PAIKKAUSTYYPPI, :kasin-maaritelty)
ON CONFLICT ("paikkaus-id") WHERE paikkaustyyppi = 'reikapaikkaus' DO
UPDATE
SET "paikkaus-id"      = :paikkaus-id,
    ajorata            = :ajorata,
    kaista             = :kaista,
    paikkaustyyppi     = 'reikapaikkaus'::PAIKKAUSTYYPPI,
    "kasin-maaritelty" = :kasin-maaritelty;

-- name: paivita-reikapaikkaus!
UPDATE paikkaus SET
  "muokkaaja-id"          = COALESCE(:muokkaaja-id::INT, NULL::INT),
  muokattu                = NOW()::TIMESTAMP, 
  alkuaika                = COALESCE(:alkuaika::TIMESTAMP, alkuaika),
  loppuaika               = COALESCE(:loppuaika::TIMESTAMP, loppuaika),
  tierekisteriosoite      = ROW(:tie, :aosa, :aet, :losa, :let, NULL)::TR_OSOITE, 
  tyomenetelma            = COALESCE(:tyomenetelma-id, (SELECT id FROM paikkauskohde_tyomenetelma WHERE nimi = :tyomenetelma)),
  kustannus               = :kustannus::NUMERIC,
  "reikapaikkaus-yksikko" = :yksikko::TEXT, 
  maara                   = :maara::INT,
  luokittelu              = :luokittelu::TEXT[],
  sijainti                = (SELECT tieosoitteelle_geometria(:tie::INT, :aosa::INT, :aet::INT, :losa::INT, :let::INT)),
  poistettu               = FALSE -- Jos tuodaan jo poistettu rivi, merkataan se ei-poistetuksi 
WHERE "urakka-id" = :urakka-id AND "ulkoinen-id" = :ulkoinen-id;

-- name: paivita-reikapaikkauksen-tienkohta!
UPDATE paikkauksen_tienkohta
SET ajorata = :ajorata,
    kaista = :kaista,
    "kasin-maaritelty" = :kasin-maaritelty
WHERE "paikkaus-id" = :paikkaus-id;


-- name: hae-reikapaikkaus-vaikka-poistettu
-- Tätä käytetään myös API:n kautta. On tärkeää löytää myös poistetut reikäpaikkaukset, jotta niitä voidaan muokata.
-- Jos tämä ei palauta poistettuja, niin API:n kautta samalla tunnisteella lähetettävä päivityspyyntö ei onnistu.
SELECT *
  FROM paikkaus
 WHERE "paikkaus-tyyppi" = 'reikapaikkaus'
   AND "urakka-id" = :urakka-id
   AND "ulkoinen-id" = :ulkoinen-id;

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

-- name: hae-reikapaikkausid
-- single?: true
SELECT id
  FROM paikkaus
 WHERE "paikkaus-tyyppi" = 'reikapaikkaus'
   AND "urakka-id" = :urakka-id
   AND "ulkoinen-id" = :ulkoinen-id;

-- name: laske-pkluokka-reikapaikkaukselle!
SELECT * FROM paivita_reikapaikkauksen_korjausluokka(:id);
