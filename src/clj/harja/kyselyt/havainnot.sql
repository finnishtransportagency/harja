-- name: hae-havainnot-tilannekuvaan
-- Hakee havainnot urakasta aikavälillä. Muuten voitaisiin käyttää esim hae-kaikki-havainnot, mutta siinä
-- filtteröidään pois suorasanktiona luodut havainnot. Historiakuvassa näin ei haluta tehdä.
SELECT
  h.id,
  h.aika,
  h.kohde,
  h.tekija,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
  h.kasittelyaika                    AS paatos_kasittelyaika,
  h.paatos                           AS paatos_paatos,
  h.kasittelytapa                    AS paatos_kasittelytapa,
  h.kuvaus,
  h.sijainti
FROM havainto h
  JOIN kayttaja k ON h.luoja = k.id
  LEFT JOIN sanktio s ON h.id=s.havainto
WHERE h.urakka = :urakka
      AND (aika >= :alku AND aika <= :loppu);

-- name: hae-kaikki-havainnot
-- Hakee listaukseen kaikki urakan havainnot annetulle aikavälille
-- Ei palauta havaintoja, joiden sanktio on suorasanktio - eli sanktio on tehty suoraan Sanktiot-
-- välilehden kautta, ja havainto on luotu käytännössä vain tietomallin vaatimusten vuoksi.
SELECT
  h.id,
  h.aika,
  h.kohde,
  h.tekija,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
  h.kasittelyaika                    AS paatos_kasittelyaika,
  h.paatos                           AS paatos_paatos,
  h.kasittelytapa                    AS paatos_kasittelytapa,
  h.kuvaus,
  h.sijainti
FROM havainto h
  JOIN kayttaja k ON h.luoja = k.id
  LEFT JOIN sanktio s ON h.id=s.havainto
WHERE h.urakka = :urakka
      AND (aika >= :alku AND aika <= :loppu)
      AND s.suorasanktio IS NOT TRUE;

-- name: hae-selvitysta-odottavat-havainnot
-- Hakee listaukseen kaikki urakan havainnot, jotka odottavat urakoitsijalta selvitystä.
-- Ei palauta havaintoja, joiden sanktio on suorasanktio - eli sanktio on tehty suoraan Sanktiot-
-- välilehden kautta, ja havainto on luotu käytännössä vain tietomallin vaatimusten vuoksi.
SELECT
  h.id,
  h.aika,
  h.kohde,
  h.tekija,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
  h.kasittelyaika                    AS paatos_kasittelyaika,
  h.paatos                           AS paatos_paatos,
  h.kasittelytapa                    AS paatos_kasittelytapa,
  h.kuvaus,
  h.sijainti,
  (SELECT k.kommentti
   FROM kommentti k
   WHERE k.id IN (SELECT hk.kommentti
                  FROM havainto_kommentti hk
                  WHERE hk.havainto = h.id)
   ORDER BY luotu ASC
   OFFSET 0
   LIMIT 1)                          AS kommentti
FROM havainto h
  JOIN kayttaja k ON h.luoja = k.id
  LEFT JOIN sanktio s ON s.havainto = h.id
WHERE h.urakka = :urakka
      AND (aika >= :alku AND aika <= :loppu)
      AND selvitys_pyydetty = TRUE AND selvitys_annettu = FALSE
      AND s.suorasanktio IS NOT TRUE;

-- name: hae-kasitellyt-havainnot
-- Hakee listaukseen kaikki urakan havainnot, jotka on käsitelty.
SELECT
  h.id,
  h.aika,
  h.kohde,
  h.tekija,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
  h.kasittelyaika                    AS paatos_kasittelyaika,
  h.paatos                           AS paatos_paatos,
  h.kasittelytapa                    AS paatos_kasittelytapa,
  h.kuvaus,
  h.sijainti,
  (SELECT k.kommentti
   FROM kommentti k
   WHERE k.id IN (SELECT hk.kommentti
                  FROM havainto_kommentti hk
                  WHERE hk.havainto = h.id)
   ORDER BY luotu ASC
   OFFSET 0
   LIMIT 1)                          AS kommentti
FROM havainto h
  JOIN kayttaja k ON h.luoja = k.id
  LEFT JOIN sanktio s ON s.havainto=h.id
WHERE h.urakka = :urakka
      AND (aika >= :alku AND aika <= :loppu)
      AND paatos IS NOT NULL
      AND s.suorasanktio IS NOT TRUE;

-- name: hae-omat-havainnot
-- Hakee listaukseen kaikki urakan havainnot, joiden luoja tai kommentoija on annettu henkilö.
-- Ei palauta havaintoja, joiden sanktio on suorasanktio - eli sanktio on tehty suoraan Sanktiot-
-- välilehden kautta, ja havainto on luotu käytännössä vain tietomallin vaatimusten vuoksi.
SELECT
  h.id,
  h.aika,
  h.kohde,
  h.tekija,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
  h.kasittelyaika                    AS paatos_kasittelyaika,
  h.paatos                           AS paatos_paatos,
  h.kasittelytapa                    AS paatos_kasittelytapa,
  h.kuvaus,
  h.sijainti,
  (SELECT k.kommentti
   FROM kommentti k
   WHERE k.id IN (SELECT hk.kommentti
                  FROM havainto_kommentti hk
                  WHERE hk.havainto = h.id)
   ORDER BY luotu ASC
   OFFSET 0
   LIMIT 1)                          AS kommentti
FROM havainto h
  JOIN kayttaja k ON h.luoja = k.id
  LEFT JOIN sanktio s ON s.havainto = h.id
WHERE h.urakka = :urakka
      AND (aika >= :alku AND aika <= :loppu)
      AND (h.luoja = :kayttaja OR
           h.id IN (SELECT hk.havainto
                    FROM havainto_kommentti hk JOIN kommentti k ON hk.kommentti = k.id
                    WHERE k.luoja = :kayttaja))
      AND s.suorasanktio IS NOT TRUE;


-- name: hae-havainnon-tiedot
-- Hakee havainnon tiedot muokkausnäkymiin.
SELECT
  h.id,
  h.aika,
  h.kohde,
  h.tekija,
  h.kuvaus,
  h.sijainti,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
  h.kasittelyaika                    AS paatos_kasittelyaika,
  h.paatos                           AS paatos_paatos,
  h.kasittelytapa                    AS paatos_kasittelytapa,
  h.perustelu                        AS paatos_perustelu,
  h.muu_kasittelytapa                AS paatos_muukasittelytapa,
  h.selvitys_pyydetty                AS selvityspyydetty
FROM havainto h
  JOIN kayttaja k ON h.luoja = k.id
WHERE h.urakka = :urakka
      AND h.id = :id;

-- name: hae-havainnon-kommentit
-- Hakee annetun havainnon kaikki kommentit (joita ei ole poistettu) sekä
-- kommentin mahdollisen liitteen tiedot. Kommentteja on vaikea hakea
-- array aggregoimalla itse havainnon hakukyselyssä.
SELECT
  k.id,
  k.tekija,
  k.kommentti,
  k.luoja,
  k.luotu                              AS aika,
  CONCAT(ka.etunimi, ' ', ka.sukunimi) AS tekijanimi,
  l.id                                 AS liite_id,
  l.tyyppi                             AS liite_tyyppi,
  l.koko                               AS liite_koko,
  l.nimi                               AS liite_nimi,
  l.liite_oid                          AS liite_oid
FROM kommentti k
  JOIN kayttaja ka ON k.luoja = ka.id
  LEFT JOIN liite l ON l.id = k.liite
WHERE k.poistettu = FALSE
      AND k.id IN (SELECT hk.kommentti
                   FROM havainto_kommentti hk
                   WHERE hk.havainto = :id)
ORDER BY k.luotu ASC;


-- name: hae-havainnon-liitteet
-- Hakee annetun havainnon kaikki liitteet
SELECT
  l.id                                 AS id,
  l.tyyppi                             AS tyyppi,
  l.koko                               AS koko,
  l.nimi                               AS nimi,
  l.liite_oid                          AS oid
FROM liite l
  JOIN havainto_liite hl on l.id = hl.liite
WHERE hl.havainto = :havaintoid
ORDER BY l.luotu ASC;

-- name: paivita-havainnon-perustiedot<!
-- Päivittää aiemmin luodun havainnon perustiedot
UPDATE havainto
SET aika            = :aika,
  tekija            = :tekija :: osapuoli,
  kohde             = :kohde,
  selvitys_pyydetty = :selvitys,
  muokkaaja         = :muokkaaja,
  kuvaus            = :kuvaus,
  muokattu          = current_timestamp
WHERE id = :id;

-- name: luo-havainto<!
-- Luo uuden havainnon annetuille perustiedoille. Luontivaiheessa ei
-- voi antaa päätöstietoja.
INSERT
INTO havainto
(urakka, aika, tekija, kohde, selvitys_pyydetty, luoja, luotu, kuvaus, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, ulkoinen_id)
VALUES (:urakka, :aika, :tekija :: osapuoli, :kohde, :selvitys, :luoja, current_timestamp, :kuvaus,
        POINT(:x_koordinaatti, :y_koordinaatti)::GEOMETRY, :tr_numero, :tr_alkuosa, :tr_loppuosa, :tr_alkuetaisyys,
        :tr_loppuetaisyys, :ulkoinen_id);

-- name: kirjaa-havainnon-paatos!
-- Kirjaa havainnolle päätöksen.
UPDATE havainto
SET kasittelyaika   = :kasittelyaika,
  paatos            = :paatos :: havainnon_paatostyyppi,
  perustelu         = :perustelu,
  kasittelytapa     = :kasittelytapa :: havainnon_kasittelytapa,
  muu_kasittelytapa = :muukasittelytapa,
  muokkaaja         = :muokkaaja,
  muokattu          = current_timestamp
WHERE id = :id;

-- name: liita-kommentti<!
-- Liittää havaintoon uuden kommentin
INSERT INTO havainto_kommentti (havainto, kommentti) VALUES (:havainto, :kommentti);

-- name: liita-liite<!
-- Liittää havaintoon uuden liitteen
INSERT INTO havainto_liite (havainto, liite) VALUES (:havainto, :liite);

-- name: liita-havainto<!
-- Liittää havaintoon uuden liitteen
INSERT INTO havainto_liite (havainto, liite) VALUES (:havainto, :liite);

-- name: onko-olemassa-ulkoisella-idlla
-- Tarkistaa löytyykö havaintoa ulkoisella id:llä
SELECT exists(
    SELECT havainto.id
    FROM havainto
    WHERE ulkoinen_id = :ulkoinen_id AND luoja = :luoja);

-- name: paivita-havainto-ulkoisella-idlla<!
-- Päivittää havainnon annetuille perustiedoille.
UPDATE havainto
SET
  aika             = :aika,
  kohde            = :kohde,
  kuvaus           = :kuvaus,
  sijainti         = POINT(:x_koordinaatti, :y_koordinaatti),
  tr_numero        = :tr_numero,
  tr_alkuosa       = :tr_alkuosa,
  tr_loppuosa      = :tr_loppuosa,
  tr_alkuetaisyys  = :tr_alkuetaisyys,
  tr_loppuetaisyys = :tr_loppuetaisyys,
  muokkaaja        = :muokkaaja,
  muokattu         = current_timestamp
WHERE ulkoinen_id = :ulkoinen_id AND
      luoja = :luoja;


