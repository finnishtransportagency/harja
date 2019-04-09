-- name: hae-kaikki-laatupoikkeamat
-- Hakee listaukseen kaikki urakan laatupoikkeamat annetulle aikavälille
-- Ei palauta laatupoikkeamia, joiden sanktio on suorasanktio - eli sanktio on tehty suoraan Sanktiot-
-- välilehden kautta, ja laatupoikkeama on luotu käytännössä vain tietomallin vaatimusten vuoksi.
SELECT
  lp.id,
  lp.aika,
  lp.kohde,
  lp.tekija,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
  lp.kasittelyaika                   AS paatos_kasittelyaika,
  lp.paatos                          AS paatos_paatos,
  lp.kasittelytapa                   AS paatos_kasittelytapa,
  lp.kuvaus,
  lp.tr_numero,
  lp.tr_alkuosa,
  lp.tr_alkuetaisyys,
  lp.tr_loppuosa,
  lp.tr_loppuetaisyys,
  lp.sijainti,
  lp."sisaltaa-poikkeamaraportin?",
  ypk.tr_numero        AS yllapitokohde_tr_numero,
  ypk.tr_alkuosa       AS yllapitokohde_tr_alkuosa,
  ypk.tr_alkuetaisyys  AS yllapitokohde_tr_alkuetaisyys,
  ypk.tr_loppuosa      AS yllapitokohde_tr_loppuosa,
  ypk.tr_loppuetaisyys AS yllapitokohde_tr_loppuetaisyys,
  ypk.kohdenumero      AS yllapitokohde_numero,
  ypk.nimi             AS yllapitokohde_nimi
FROM laatupoikkeama lp
  JOIN kayttaja k ON lp.luoja = k.id
  LEFT JOIN sanktio s ON lp.id = s.laatupoikkeama AND s.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id
WHERE lp.urakka = :urakka
      AND lp.poistettu IS NOT TRUE
      AND (aika >= :alku AND aika <= :loppu)
      AND s.suorasanktio IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (lp.yllapitokohde IS NULL
          OR
          lp.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS NOT TRUE);

-- name: hae-selvitysta-odottavat-laatupoikkeamat
-- Hakee listaukseen kaikki urakan laatupoikkeamat, jotka odottavat urakoitsijalta selvitystä.
-- Ei palauta laatupoikkeamia, joiden sanktio on suorasanktio - eli sanktio on tehty suoraan Sanktiot-
-- välilehden kautta, ja laatupoikkeama on luotu käytännössä vain tietomallin vaatimusten vuoksi.
SELECT
  lp.id,
  lp.aika,
  lp.kohde,
  lp.tekija,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
  lp.kasittelyaika                   AS paatos_kasittelyaika,
  lp.paatos                          AS paatos_paatos,
  lp.kasittelytapa                   AS paatos_kasittelytapa,
  lp.kuvaus,
  lp.tr_numero,
  lp.tr_alkuosa,
  lp.tr_alkuetaisyys,
  lp.tr_loppuosa,
  lp.tr_loppuetaisyys,
  lp.sijainti,
  lp."sisaltaa-poikkeamaraportin?",
  ypk.tr_numero        AS yllapitokohde_tr_numero,
  ypk.tr_alkuosa       AS yllapitokohde_tr_alkuosa,
  ypk.tr_alkuetaisyys  AS yllapitokohde_tr_alkuetaisyys,
  ypk.tr_loppuosa      AS yllapitokohde_tr_loppuosa,
  ypk.tr_loppuetaisyys AS yllapitokohde_tr_loppuetaisyys,
  ypk.kohdenumero      AS yllapitokohde_numero,
  ypk.nimi             AS yllapitokohde_nimi
FROM laatupoikkeama lp
  JOIN kayttaja k ON lp.luoja = k.id
  LEFT JOIN sanktio s ON s.laatupoikkeama = lp.id AND s.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id
WHERE lp.urakka = :urakka
      AND lp.poistettu IS NOT TRUE
      AND (aika >= :alku AND aika <= :loppu)
      AND selvitys_pyydetty = TRUE AND selvitys_annettu = FALSE
      AND s.suorasanktio IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (lp.yllapitokohde IS NULL
          OR
          lp.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS NOT TRUE);

-- name: hae-kasitellyt-laatupoikkeamat
-- Hakee listaukseen kaikki urakan laatupoikkeamat, jotka on käsitelty.
SELECT
  lp.id,
  lp.aika,
  lp.kohde,
  lp.tekija,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
  lp.kasittelyaika                   AS paatos_kasittelyaika,
  lp.paatos                          AS paatos_paatos,
  lp.kasittelytapa                   AS paatos_kasittelytapa,
  lp.kuvaus,
  lp.tr_numero,
  lp.tr_alkuosa,
  lp.tr_alkuetaisyys,
  lp.tr_loppuosa,
  lp.tr_loppuetaisyys,
  lp.sijainti,
  lp."sisaltaa-poikkeamaraportin?",
  ypk.tr_numero        AS yllapitokohde_tr_numero,
  ypk.tr_alkuosa       AS yllapitokohde_tr_alkuosa,
  ypk.tr_alkuetaisyys  AS yllapitokohde_tr_alkuetaisyys,
  ypk.tr_loppuosa      AS yllapitokohde_tr_loppuosa,
  ypk.tr_loppuetaisyys AS yllapitokohde_tr_loppuetaisyys,
  ypk.kohdenumero      AS yllapitokohde_numero,
  ypk.nimi             AS yllapitokohde_nimi
FROM laatupoikkeama lp
  JOIN kayttaja k ON lp.luoja = k.id
  LEFT JOIN sanktio s ON s.laatupoikkeama = lp.id AND s.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id
WHERE lp.urakka = :urakka
      AND lp.poistettu IS NOT TRUE
      AND (aika >= :alku AND aika <= :loppu)
      AND paatos IS NOT NULL
      AND s.suorasanktio IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (lp.yllapitokohde IS NULL
          OR
          lp.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS NOT TRUE);

-- name: hae-omat-laatupoikkeamat
-- Hakee listaukseen kaikki urakan laatupoikkeamat, joiden luoja tai kommentoija on annettu henkilö.
-- Ei palauta laatupoikkeamia, joiden sanktio on suorasanktio - eli sanktio on tehty suoraan Sanktiot-
-- välilehden kautta, ja laatupoikkeama on luotu käytännössä vain tietomallin vaatimusten vuoksi.
SELECT
  lp.id,
  lp.aika,
  lp.kohde,
  lp.tekija,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
  lp.kasittelyaika                   AS paatos_kasittelyaika,
  lp.paatos                          AS paatos_paatos,
  lp.kasittelytapa                   AS paatos_kasittelytapa,
  lp.kuvaus,
  lp.tr_numero,
  lp.tr_alkuosa,
  lp.tr_alkuetaisyys,
  lp.tr_loppuosa,
  lp.tr_loppuetaisyys,
  lp.sijainti,
  lp."sisaltaa-poikkeamaraportin?",
  ypk.tr_numero        AS yllapitokohde_tr_numero,
  ypk.tr_alkuosa       AS yllapitokohde_tr_alkuosa,
  ypk.tr_alkuetaisyys  AS yllapitokohde_tr_alkuetaisyys,
  ypk.tr_loppuosa      AS yllapitokohde_tr_loppuosa,
  ypk.tr_loppuetaisyys AS yllapitokohde_tr_loppuetaisyys,
  ypk.kohdenumero      AS yllapitokohde_numero,
  ypk.nimi             AS yllapitokohde_nimi
FROM laatupoikkeama lp
  JOIN kayttaja k ON lp.luoja = k.id
  LEFT JOIN sanktio s ON s.laatupoikkeama = lp.id AND s.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id
WHERE lp.urakka = :urakka
      AND lp.poistettu IS NOT TRUE
      AND (aika >= :alku AND aika <= :loppu)
      AND (lp.luoja = :kayttaja OR
           lp.id IN (SELECT hk.laatupoikkeama
                     FROM laatupoikkeama_kommentti hk JOIN kommentti k ON hk.kommentti = k.id
                    WHERE k.luoja = :kayttaja))
      AND s.suorasanktio IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (lp.yllapitokohde IS NULL
          OR
          lp.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS NOT TRUE);


-- name: hae-poikkeamaraportilliset-laatupoikkeamat
-- Hakee listaukseen kaikki laatupoikkeamat, joilla on poikkeamaraportti
SELECT
  lp.id,
  lp.aika,
  lp.kohde,
  lp.tekija,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
  lp.kasittelyaika                   AS paatos_kasittelyaika,
  lp.paatos                          AS paatos_paatos,
  lp.kasittelytapa                   AS paatos_kasittelytapa,
  lp.kuvaus,
  lp.tr_numero,
  lp.tr_alkuosa,
  lp.tr_alkuetaisyys,
  lp.tr_loppuosa,
  lp.tr_loppuetaisyys,
  lp.sijainti,
  lp."sisaltaa-poikkeamaraportin?",
  ypk.tr_numero                      AS yllapitokohde_tr_numero,
  ypk.tr_alkuosa                     AS yllapitokohde_tr_alkuosa,
  ypk.tr_alkuetaisyys                AS yllapitokohde_tr_alkuetaisyys,
  ypk.tr_loppuosa                    AS yllapitokohde_tr_loppuosa,
  ypk.tr_loppuetaisyys               AS yllapitokohde_tr_loppuetaisyys,
  ypk.kohdenumero                    AS yllapitokohde_numero,
  ypk.nimi                           AS yllapitokohde_nimi
FROM laatupoikkeama lp
  JOIN kayttaja k ON lp.luoja = k.id
  LEFT JOIN sanktio s ON lp.id = s.laatupoikkeama AND s.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id
WHERE lp.urakka = :urakka
      AND lp.poistettu IS NOT TRUE
      AND lp."sisaltaa-poikkeamaraportin?" IS TRUE
      AND (aika >= :alku AND aika <= :loppu)
      AND s.suorasanktio IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (lp.yllapitokohde IS NULL
           OR
           lp.yllapitokohde IS NOT NULL AND
           (SELECT poistettu
            FROM yllapitokohde
            WHERE id = lp.yllapitokohde) IS NOT TRUE);

-- name: hae-laatupoikkeaman-tiedot
-- Hakee laatupoikkeaman tiedot muokkausnäkymiin.
SELECT
  lp.id,
  lp.aika,
  lp.kohde,
  lp.tekija,
  lp.kuvaus,
  lp.sijainti,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
  lp.kasittelyaika                   AS paatos_kasittelyaika,
  lp.paatos                          AS paatos_paatos,
  lp.kasittelytapa                   AS paatos_kasittelytapa,
  lp.perustelu                       AS paatos_perustelu,
  lp.muu_kasittelytapa               AS paatos_muukasittelytapa,
  lp.selvitys_pyydetty               AS selvityspyydetty,
  lp.tr_numero,
  lp.tr_alkuosa,
  lp.tr_alkuetaisyys,
  lp.tr_loppuosa,
  lp.tr_loppuetaisyys,
  lp."sisaltaa-poikkeamaraportin?",
  tl.tarkastus                       AS tarkastusid,
  t.nayta_urakoitsijalle             AS "nayta-tarkastus-urakoitsijalle",
  ypk.id                             AS "yllapitokohde_id",
  ypk.tr_numero                      AS "yllapitokohde_tr-numero",
  ypk.tr_alkuosa                     AS "yllapitokohde_tr-alkuosa",
  ypk.tr_alkuetaisyys                AS "yllapitokohde_tr-alkuetaisyys",
  ypk.tr_loppuosa                    AS "yllapitokohde_tr-loppuosa",
  ypk.tr_loppuetaisyys               AS "yllapitokohde_tr-loppuetaisyys",
  ypk.kohdenumero                    AS "yllapitokohde_numero",
  ypk.nimi                           AS "yllapitokohde_nimi"
FROM laatupoikkeama lp
  JOIN kayttaja k ON lp.luoja = k.id
  LEFT JOIN tarkastus_laatupoikkeama tl on lp.id = tl.laatupoikkeama
  LEFT JOIN tarkastus t ON tl.tarkastus = t.id
  LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id
WHERE lp.urakka = :urakka
      AND lp.poistettu IS NOT TRUE
      AND lp.id = :id
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (lp.yllapitokohde IS NULL
          OR
          lp.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS NOT TRUE);

-- name: hae-laatupoikkeaman-kommentit
-- Hakee annetun laatupoikkeaman kaikki kommentit (joita ei ole poistettu) sekä
-- kommentin mahdollisen liitteen tiedot. Kommentteja on vaikea hakea
-- array aggregoimalla itse laatupoikkeaman hakukyselyssä.
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
WHERE k.poistettu IS NOT TRUE
      AND k.id IN (SELECT hk.kommentti
                   FROM laatupoikkeama_kommentti hk
                   WHERE hk.laatupoikkeama = :id)
ORDER BY k.luotu ASC;


-- name: hae-laatupoikkeaman-liitteet
-- Hakee annetun laatupoikkeaman kaikki liitteet
SELECT
  l.id        AS id,
  l.tyyppi    AS tyyppi,
  l.koko      AS koko,
  l.nimi      AS nimi,
  l.liite_oid AS oid
FROM liite l
  JOIN laatupoikkeama_liite hl ON l.id = hl.liite
WHERE hl.laatupoikkeama = :laatupoikkeamaid
ORDER BY l.luotu ASC;

-- name: paivita-laatupoikkeaman-perustiedot<!
-- Päivittää aiemmin luodun laatupoikkeaman perustiedot
UPDATE laatupoikkeama
SET aika                        = :aika,
  tekija                        = :tekija :: OSAPUOLI,
  kohde                         = :kohde,
  selvitys_pyydetty             = :selvitys,
  muokkaaja                     = :muokkaaja,
  kuvaus                        = :kuvaus,
  sijainti                      = :sijainti,
  tr_numero                     = :numero,
  tr_alkuosa                    = :alkuosa,
  tr_loppuosa                   = :loppuosa,
  tr_alkuetaisyys               = :alkuetaisyys,
  tr_loppuetaisyys              = :loppuetaisyys,
  yllapitokohde                 = :yllapitokohde,
  muokattu                      = current_timestamp,
  "sisaltaa-poikkeamaraportin?" = :sisaltaa_laatupoikkeaman,
  poistettu                     = :poistettu
WHERE id = :id
AND urakka = :urakka;

-- name: luo-laatupoikkeama<!
-- Luo uuden laatupoikkeaman annetuille perustiedoille. Luontivaiheessa ei
-- voi antaa päätöstietoja.
INSERT
INTO laatupoikkeama
(lahde,
 urakka,
 aika,
 tekija,
 kohde,
 selvitys_pyydetty,
 luoja,
 luotu,
 kuvaus,
 sijainti,
 tr_numero,
 tr_alkuosa,
 tr_loppuosa,
 tr_alkuetaisyys,
 tr_loppuetaisyys,
 yllapitokohde,
 "sisaltaa-poikkeamaraportin?",
 ulkoinen_id)
VALUES (:lahde :: LAHDE, :urakka, :aika, :tekija :: OSAPUOLI, :kohde, :selvitys, :luoja, current_timestamp, :kuvaus,
        :sijainti :: GEOMETRY, :tr_numero, :tr_alkuosa, :tr_loppuosa, :tr_alkuetaisyys,
        :tr_loppuetaisyys, :yllapitokohde, :sisaltaa_laatupoikkeaman, :ulkoinen_id);

-- name: kirjaa-laatupoikkeaman-paatos!
-- Kirjaa havainnolle päätöksen.
UPDATE laatupoikkeama
SET kasittelyaika   = :kasittelyaika,
  paatos            = :paatos :: laatupoikkeaman_paatostyyppi,
  perustelu         = :perustelu,
  kasittelytapa     = :kasittelytapa :: laatupoikkeaman_kasittelytapa,
  muu_kasittelytapa = :muukasittelytapa,
  muokkaaja         = :muokkaaja,
  muokattu          = current_timestamp
WHERE id = :id;

-- name: liita-kommentti<!
-- Liittää laatupoikkeamaon uuden kommentin
INSERT INTO laatupoikkeama_kommentti (laatupoikkeama, kommentti) VALUES (:laatupoikkeama, :kommentti);

-- name: liita-liite<!
-- Liittää laatupoikkeamaon uuden liitteen
INSERT INTO laatupoikkeama_liite (laatupoikkeama, liite) VALUES (:laatupoikkeama, :liite);

-- name: liita-laatupoikkeama<!
-- Liittää laatupoikkeamalle uuden liitteen
INSERT INTO laatupoikkeama_liite (laatupoikkeama, liite) VALUES (:laatupoikkeama, :liite);

-- name: onko-olemassa-ulkoisella-idlla
-- Tarkistaa löytyykö laatupoikkeamaa ulkoisella id:llä
SELECT exists(
    SELECT laatupoikkeama.id
    FROM laatupoikkeama
    WHERE ulkoinen_id = :ulkoinen_id AND luoja = :luoja);

-- name: paivita-laatupoikkeama-ulkoisella-idlla<!
-- Päivittää laatupoikkeaman annetuille perustiedoille.
UPDATE laatupoikkeama
SET
  aika             = :aika,
  kohde            = :kohde,
  kuvaus           = :kuvaus,
  sijainti         = :sijainti :: GEOMETRY,
  tr_numero        = :tr_numero,
  tr_alkuosa       = :tr_alkuosa,
  tr_loppuosa      = :tr_loppuosa,
  tr_alkuetaisyys  = :tr_alkuetaisyys,
  tr_loppuetaisyys = :tr_loppuetaisyys,
  muokkaaja        = :muokkaaja,
  muokattu         = current_timestamp,
  "sisaltaa-poikkeamaraportin?" = :sisaltaa_poikkeamaraportin
WHERE ulkoinen_id = :ulkoinen_id AND
      luoja = :luoja;

-- name: hae-urakan-laatupoikkeamat-liitteineen-raportille
-- Hakee urakan laatupoikkeamat aikavälin perusteella raportille
SELECT
  lp.id,
  lp.aika,
  lp.kohde,
  lp.kuvaus,
  lp.tekija,
  liite.id   as liite_id,
  liite.nimi as liite_nimi,
  liite.tyyppi as liite_tyyppi,
  liite.koko as liite_koko,
  liite.liite_oid as liite_oid,
  ypk.nimi as yllapitokohde_nimi,
  ypk.tr_numero as yllapitokohde_tie,
  ypk.tr_alkuosa as yllapitokohde_aosa,
  ypk.tr_alkuetaisyys as yllapitokohde_aet,
  ypk.tr_loppuosa as yllapitokohde_losa,
  ypk.tr_loppuetaisyys as yllapitokohde_let,
  ypk.kohdenumero as yllapitokohde_kohdenumero
FROM laatupoikkeama lp
  JOIN kayttaja k ON lp.luoja = k.id
  JOIN organisaatio o ON k.organisaatio = o.id
  LEFT JOIN laatupoikkeama_liite ON lp.id = laatupoikkeama_liite.laatupoikkeama
  LEFT JOIN liite ON laatupoikkeama_liite.liite = liite.id
  LEFT JOIN  yllapitokohde ypk ON lp.yllapitokohde = ypk.id
WHERE lp.urakka = :urakka
      AND lp.poistettu IS NOT TRUE
      AND (lp.aika >= :alku AND lp.aika <= :loppu)
      AND (:rajaa_tekijalla = FALSE OR lp.tekija = :tekija::osapuoli)
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (lp.yllapitokohde IS NULL
          OR
          lp.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS NOT TRUE);

-- name: hae-hallintayksikon-laatupoikkeamat-liitteineen-raportille
-- Hakee hallintayksikön laatupoikkeamat aikavälin perusteella raportille
SELECT
  lp.id,
  lp.aika,
  lp.kohde,
  lp.kuvaus,
  u.nimi as urakka,
  lp.tekija,
  liite.id   as liite_id,
  liite.nimi as liite_nimi,
  liite.tyyppi as liite_tyyppi,
  liite.koko as liite_koko,
  liite.liite_oid as liite_oid,
  ypk.nimi as yllapitokohde_nimi,
  ypk.tr_numero as yllapitokohde_tie,
  ypk.tr_alkuosa as yllapitokohde_aosa,
  ypk.tr_alkuetaisyys as yllapitokohde_aet,
  ypk.tr_loppuosa as yllapitokohde_losa,
  ypk.tr_loppuetaisyys as yllapitokohde_let,
  ypk.kohdenumero as yllapitokohde_kohdenumero
FROM laatupoikkeama lp
  JOIN kayttaja k ON lp.luoja = k.id
  JOIN organisaatio o ON k.organisaatio = o.id
  JOIN urakka u ON (lp.urakka = u.id AND u.urakkanro IS NOT NULL)
  LEFT JOIN laatupoikkeama_liite ON lp.id = laatupoikkeama_liite.laatupoikkeama
  LEFT JOIN liite ON laatupoikkeama_liite.liite = liite.id
  LEFT JOIN  yllapitokohde ypk ON lp.yllapitokohde = ypk.id
WHERE lp.urakka IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko
                    AND (TRUE IN (SELECT unnest(ARRAY[:urakkatyyppi]::urakkatyyppi[]) IS NULL) OR tyyppi = ANY(ARRAY[:urakkatyyppi]::urakkatyyppi[])))
      AND (lp.aika >= :alku AND lp.aika <= :loppu)
      AND lp.poistettu IS NOT TRUE
      AND (:rajaa_tekijalla = FALSE OR lp.tekija = :tekija::osapuoli)
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (lp.yllapitokohde IS NULL
          OR
          lp.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS NOT TRUE);

-- name: hae-koko-maan-laatupoikkeamat-liitteineen-raportille
-- Hakee koko maan laatupoikkeamat aikavälin perusteella raportille
SELECT
  lp.id,
  lp.aika,
  lp.kohde,
  lp.kuvaus,
  lp.tekija,
  u.nimi as urakka,
  liite.id   as liite_id,
  liite.nimi as liite_nimi,
  liite.tyyppi as liite_tyyppi,
  liite.koko as liite_koko,
  liite.liite_oid as liite_oid,
  ypk.nimi as yllapitokohde_nimi,
  ypk.tr_numero as yllapitokohde_tie,
  ypk.tr_alkuosa as yllapitokohde_aosa,
  ypk.tr_alkuetaisyys as yllapitokohde_aet,
  ypk.tr_loppuosa as yllapitokohde_losa,
  ypk.tr_loppuetaisyys as yllapitokohde_let,
  ypk.kohdenumero as yllapitokohde_kohdenumero
FROM laatupoikkeama lp
  JOIN kayttaja k ON lp.luoja = k.id
  JOIN organisaatio o ON k.organisaatio = o.id
  JOIN urakka u ON (lp.urakka = u.id AND u.urakkanro IS NOT NULL)
  LEFT JOIN laatupoikkeama_liite ON lp.id = laatupoikkeama_liite.laatupoikkeama
  LEFT JOIN liite ON laatupoikkeama_liite.liite = liite.id
  LEFT JOIN  yllapitokohde ypk ON lp.yllapitokohde = ypk.id
WHERE lp.urakka IN (SELECT id FROM urakka WHERE (TRUE IN (SELECT unnest(ARRAY[:urakkatyyppi]::urakkatyyppi[]) IS NULL) OR tyyppi = ANY(ARRAY[:urakkatyyppi]::urakkatyyppi[])))
      AND (lp.aika >= :alku AND lp.aika <= :loppu)
      AND lp.poistettu IS NOT TRUE
      AND (:rajaa_tekijalla = FALSE OR lp.tekija = :tekija::osapuoli)
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (lp.yllapitokohde IS NULL
          OR
          lp.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS NOT TRUE);

--name: hae-laatupoikkeaman-urakka-id
SELECT urakka FROM laatupoikkeama
 WHERE id = :laatupoikkeamaid;
