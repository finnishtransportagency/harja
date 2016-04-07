-- name: hae-kaikki-laatupoikkeamat
-- Hakee listaukseen kaikki urakan laatupoikkeamat annetulle aikavälille
-- Ei palauta laatupoikkeamia, joiden sanktio on suorasanktio - eli sanktio on tehty suoraan Sanktiot-
-- välilehden kautta, ja laatupoikkeama on luotu käytännössä vain tietomallin vaatimusten vuoksi.
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
FROM laatupoikkeama h
  JOIN kayttaja k ON h.luoja = k.id
  LEFT JOIN sanktio s ON h.id = s.laatupoikkeama
WHERE h.urakka = :urakka
      AND (aika >= :alku AND aika <= :loppu)
      AND s.suorasanktio IS NOT TRUE;

-- name: hae-selvitysta-odottavat-laatupoikkeamat
-- Hakee listaukseen kaikki urakan laatupoikkeamat, jotka odottavat urakoitsijalta selvitystä.
-- Ei palauta laatupoikkeamia, joiden sanktio on suorasanktio - eli sanktio on tehty suoraan Sanktiot-
-- välilehden kautta, ja laatupoikkeama on luotu käytännössä vain tietomallin vaatimusten vuoksi.
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
                  FROM laatupoikkeama_kommentti hk
                  WHERE hk.laatupoikkeama = h.id)
   ORDER BY luotu ASC
   OFFSET 0
   LIMIT 1)                          AS kommentti
FROM laatupoikkeama h
  JOIN kayttaja k ON h.luoja = k.id
  LEFT JOIN sanktio s ON s.laatupoikkeama = h.id
WHERE h.urakka = :urakka
      AND (aika >= :alku AND aika <= :loppu)
      AND selvitys_pyydetty = TRUE AND selvitys_annettu = FALSE
      AND s.suorasanktio IS NOT TRUE;

-- name: hae-kasitellyt-laatupoikkeamat
-- Hakee listaukseen kaikki urakan laatupoikkeamat, jotka on käsitelty.
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
                  FROM laatupoikkeama_kommentti hk
                  WHERE hk.laatupoikkeama = h.id)
   ORDER BY luotu ASC
   OFFSET 0
   LIMIT 1)                          AS kommentti
FROM laatupoikkeama h
  JOIN kayttaja k ON h.luoja = k.id
  LEFT JOIN sanktio s ON s.laatupoikkeama = h.id
WHERE h.urakka = :urakka
      AND (aika >= :alku AND aika <= :loppu)
      AND paatos IS NOT NULL
      AND s.suorasanktio IS NOT TRUE;

-- name: hae-omat-laatupoikkeamat
-- Hakee listaukseen kaikki urakan laatupoikkeamat, joiden luoja tai kommentoija on annettu henkilö.
-- Ei palauta laatupoikkeamia, joiden sanktio on suorasanktio - eli sanktio on tehty suoraan Sanktiot-
-- välilehden kautta, ja laatupoikkeama on luotu käytännössä vain tietomallin vaatimusten vuoksi.
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
                  FROM laatupoikkeama_kommentti hk
                  WHERE hk.laatupoikkeama = h.id)
   ORDER BY luotu ASC
   OFFSET 0
   LIMIT 1)                          AS kommentti
FROM laatupoikkeama h
  JOIN kayttaja k ON h.luoja = k.id
  LEFT JOIN sanktio s ON s.laatupoikkeama = h.id
WHERE h.urakka = :urakka
      AND (aika >= :alku AND aika <= :loppu)
      AND (h.luoja = :kayttaja OR
           h.id IN (SELECT hk.laatupoikkeama
                    FROM laatupoikkeama_kommentti hk JOIN kommentti k ON hk.kommentti = k.id
                    WHERE k.luoja = :kayttaja))
      AND s.suorasanktio IS NOT TRUE;


-- name: hae-laatupoikkeaman-tiedot
-- Hakee laatupoikkeaman tiedot muokkausnäkymiin.
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
  h.selvitys_pyydetty                AS selvityspyydetty,
  h.tr_numero,
  h.tr_alkuosa,
  h.tr_alkuetaisyys,
  h.tr_loppuosa,
  h.tr_loppuetaisyys
FROM laatupoikkeama h
  JOIN kayttaja k ON h.luoja = k.id
WHERE h.urakka = :urakka
      AND h.id = :id;

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
WHERE k.poistettu = FALSE
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
SET aika            = :aika,
  tekija            = :tekija :: osapuoli,
  kohde             = :kohde,
  selvitys_pyydetty = :selvitys,
  muokkaaja         = :muokkaaja,
  kuvaus            = :kuvaus,
  sijainti = :sijainti,
  tr_numero = :numero,
  tr_alkuosa = :alkuosa,
  tr_loppuosa = :loppuosa,
  tr_alkuetaisyys = :alkuetaisyys,
  tr_loppuetaisyys = :loppuetaisyys,
  muokattu          = current_timestamp
WHERE id = :id;

-- name: luo-laatupoikkeama<!
-- Luo uuden laatupoikkeaman annetuille perustiedoille. Luontivaiheessa ei
-- voi antaa päätöstietoja.
INSERT
INTO laatupoikkeama
(urakka,
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
 ulkoinen_id)
VALUES (:urakka, :aika, :tekija :: osapuoli, :kohde, :selvitys, :luoja, current_timestamp, :kuvaus,
                 :sijainti :: GEOMETRY, :tr_numero, :tr_alkuosa, :tr_loppuosa, :tr_alkuetaisyys,
        :tr_loppuetaisyys, :ulkoinen_id);

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
  muokattu         = current_timestamp
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
  liite.nimi as liite_nimi
FROM laatupoikkeama lp
  JOIN kayttaja k ON lp.luoja = k.id
  JOIN organisaatio o ON k.organisaatio = o.id
  LEFT JOIN laatupoikkeama_liite ON lp.id = laatupoikkeama_liite.laatupoikkeama
  LEFT JOIN liite ON laatupoikkeama_liite.liite = liite.id
WHERE lp.urakka = :urakka
      AND (lp.aika >= :alku AND lp.aika <= :loppu)
      AND (:rajaa_tekijalla = FALSE OR lp.tekija = :tekija::osapuoli);

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
  liite.nimi as liite_nimi
FROM laatupoikkeama lp
  JOIN kayttaja k ON lp.luoja = k.id
  JOIN organisaatio o ON k.organisaatio = o.id
  JOIN urakka u ON lp.urakka = u.id
  LEFT JOIN laatupoikkeama_liite ON lp.id = laatupoikkeama_liite.laatupoikkeama
  LEFT JOIN liite ON laatupoikkeama_liite.liite = liite.id
WHERE lp.urakka IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko
                    AND (:urakkatyyppi IS NULL OR tyyppi = :urakkatyyppi :: urakkatyyppi))
      AND (lp.aika >= :alku AND lp.aika <= :loppu)
      AND (:rajaa_tekijalla = FALSE OR lp.tekija = :tekija::osapuoli);

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
  liite.nimi as liite_nimi
FROM laatupoikkeama lp
  JOIN kayttaja k ON lp.luoja = k.id
  JOIN organisaatio o ON k.organisaatio = o.id
  JOIN urakka u ON lp.urakka = u.id
  LEFT JOIN laatupoikkeama_liite ON lp.id = laatupoikkeama_liite.laatupoikkeama
  LEFT JOIN liite ON laatupoikkeama_liite.liite = liite.id
WHERE lp.urakka IN (SELECT id FROM urakka WHERE (:urakkatyyppi IS NULL OR tyyppi = :urakkatyyppi :: urakkatyyppi))
      AND (lp.aika >= :alku AND lp.aika <= :loppu)
      AND (:rajaa_tekijalla = FALSE OR lp.tekija = :tekija::osapuoli);