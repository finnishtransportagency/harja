-- name: listaa-urakat-hallintayksikolle
-- Palauttaa listan annetun hallintayksikön (id) urakoista. Sisältää perustiedot ja geometriat.
-- PENDING: joinataan mukaan ylläpidon urakat eri taulusta?
SELECT
  u.id,
  u.nimi,
  u.sampoid,
  u.alue,
  u.alkupvm,
  u.loppupvm,
  u.tyyppi,
  u.sopimustyyppi,
  hal.id                   AS hallintayksikko_id,
  hal.nimi                 AS hallintayksikko_nimi,
  hal.lyhenne              AS hallintayksikko_lyhenne,
  urk.id                   AS urakoitsija_id,
  urk.nimi                 AS urakoitsija_nimi,
  urk.ytunnus              AS urakoitsija_ytunnus,
  (SELECT array_agg(concat(id, '=', sampoid))
   FROM sopimus s
   WHERE urakka = u.id)    AS sopimukset,
  ST_Simplify(au.alue, 50) AS alueurakan_alue
FROM urakka u
  LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
  LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
  LEFT JOIN hanke h ON u.hanke = h.id
  LEFT JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro
WHERE hallintayksikko = :hallintayksikko;

-- name: hae-urakan-organisaatio
-- Hakee urakan organisaation urakka-id:llä.
SELECT
  o.nimi,
  o.ytunnus
FROM organisaatio o
  JOIN urakka u ON o.id = u.urakoitsija
WHERE u.id = :urakka;

-- name: hae-urakoita
-- Hakee urakoita tekstihaulla.
SELECT
  u.id,
  u.nimi,
  u.sampoid,
  u.alue :: POLYGON,
  u.alkupvm,
  u.loppupvm,
  u.tyyppi,
  u.sopimustyyppi,
  hal.id                   AS hallintayksikko_id,
  hal.nimi                 AS hallintayksikko_nimi,
  hal.lyhenne              AS hallintayksikko_lyhenne,
  urk.id                   AS urakoitsija_id,
  urk.nimi                 AS urakoitsija_nimi,
  urk.ytunnus              AS urakoitsija_ytunnus,
  (SELECT array_agg(concat(id, '=', sampoid))
   FROM sopimus s
   WHERE urakka = u.id)    AS sopimukset,
  ST_Simplify(au.alue, 50) AS alueurakan_alue
FROM urakka u
  LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
  LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
  LEFT JOIN hanke h ON u.hanke = h.id
  LEFT JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro
WHERE u.nimi ILIKE :teksti
      OR hal.nimi ILIKE :teksti
      OR urk.nimi ILIKE :teksti;

-- name: hae-organisaation-urakat
-- Hakee organisaation "omat" urakat, joko urakat joissa annettu hallintayksikko on tilaaja
-- tai urakat joissa annettu urakoitsija on urakoitsijana.
SELECT
  u.id,
  u.nimi,
  u.sampoid,
  u.alue :: POLYGON,
  u.alkupvm,
  u.loppupvm,
  u.tyyppi,
  u.sopimustyyppi,
  hal.id                   AS hallintayksikko_id,
  hal.nimi                 AS hallintayksikko_nimi,
  hal.lyhenne              AS hallintayksikko_lyhenne,
  urk.id                   AS urakoitsija_id,
  urk.nimi                 AS urakoitsija_nimi,
  urk.ytunnus              AS urakoitsija_ytunnus,
  (SELECT array_agg(concat(id, '=', sampoid))
   FROM sopimus s
   WHERE urakka = u.id)    AS sopimukset,
  ST_Simplify(au.alue, 50) AS alueurakan_alue
FROM urakka u
  LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
  LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
  LEFT JOIN hanke h ON u.hanke = h.id
  LEFT JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro
WHERE urk.id = :organisaatio
      OR hal.id = :organisaatio;


-- name: tallenna-urakan-sopimustyyppi!
-- Tallentaa urakalle sopimustyypin
UPDATE urakka
SET sopimustyyppi = :sopimustyyppi :: sopimustyyppi
WHERE id = :urakka;

-- name: hae-urakan-sopimustyyppi
-- Hakee urakan sopimustyypin
SELECT sopimustyyppi
FROM urakka
WHERE id = :urakka;

-- name: hae-urakoiden-tunnistetiedot
-- Hakee urakoista ydintiedot tekstihaulla.
SELECT
  u.id,
  u.nimi,
  u.hallintayksikko
FROM urakka u
  LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
  LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
WHERE u.nimi ILIKE :teksti
      OR hal.nimi ILIKE :teksti
      OR urk.nimi ILIKE :teksti;

-- name: hae-urakka
-- Hakee urakan perustiedot id:llä APIa varten.
SELECT
  u.id,
  u.nimi,
  u.tyyppi,
  u.alkupvm,
  u.loppupvm,
  urk.nimi    AS urakoitsija_nimi,
  urk.ytunnus AS urakoitsija_ytunnus
FROM urakka u
  JOIN organisaatio urk ON u.urakoitsija = urk.id
WHERE u.id = :id;

-- name: hae-urakan-sopimukset
-- Hakee urakan sopimukset urakan id:llä.
SELECT
  s.id,
  s.nimi,
  s.alkupvm,
  s.loppupvm
FROM sopimus s
WHERE s.urakka = :urakka;

-- name: onko-olemassa
-- Tarkistaa onko id:n mukaista urakkaa olemassa tietokannassa
SELECT EXISTS(SELECT id
              FROM urakka
              WHERE id = :id);
