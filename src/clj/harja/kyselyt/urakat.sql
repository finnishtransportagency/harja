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
WHERE hallintayksikko = :hallintayksikko
      AND (('hallintayksikko'::organisaatiotyyppi = :kayttajan_org_tyyppi::organisaatiotyyppi OR
            'liikennevirasto'::organisaatiotyyppi = :kayttajan_org_tyyppi::organisaatiotyyppi)
           OR ('urakoitsija'::organisaatiotyyppi = :kayttajan_org_tyyppi::organisaatiotyyppi AND
               :kayttajan_org_id = urk.id));

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
  u.hallintayksikko,
  u.sampoid
FROM urakka u
     JOIN organisaatio hal ON u.hallintayksikko = hal.id
     JOIN organisaatio urk ON u.urakoitsija = urk.id
WHERE (u.nimi ILIKE :teksti
       OR u.sampoid ILIKE :teksti)
     AND (('hallintayksikko'::organisaatiotyyppi = :kayttajan_org_tyyppi::organisaatiotyyppi OR
           'liikennevirasto'::organisaatiotyyppi = :kayttajan_org_tyyppi::organisaatiotyyppi)
          OR ('urakoitsija'::organisaatiotyyppi = :kayttajan_org_tyyppi::organisaatiotyyppi AND
              :kayttajan_org_id = urk.id))
LIMIT 11;

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

-- name: hae-urakat-ytunnuksella
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
  AND urk.ytunnus = :ytunnus;

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

-- name: paivita-hankkeen-tiedot-urakalle!
-- Päivittää hankkeen ja hallintayksikön viitteet urakalle hankkeen sampo id:n avulla
UPDATE urakka
SET hanke         = (SELECT id
                     FROM hanke
                     WHERE sampoid = :hanke_sampo_id),
  hallintayksikko = (SELECT organisaatio.id
                     FROM organisaatio organisaatio
                       LEFT JOIN alueurakka alueurakka ON alueurakka.elynumero = organisaatio.elynumero
                       LEFT JOIN hanke hanke ON alueurakka.alueurakkanro = hanke.alueurakkanro
                     WHERE hanke.sampoid = :hanke_sampo_id)
WHERE hanke_sampoid = :hanke_sampo_id;

-- name: luo-urakka<!
-- Luo uuden urakan.
INSERT INTO urakka (nimi, alkupvm, loppupvm, hanke_sampoid, sampoid, tyyppi)
VALUES (:nimi, :alkupvm, :loppupvm, :hanke_sampoid, :sampoid, :urakkatyyppi :: urakkatyyppi);

-- name: paivita-urakka!
-- Paivittaa urakan
UPDATE urakka
SET nimi = :nimi, alkupvm = :alkupvm, loppupvm = :loppupvm, hanke_sampoid = :hanke_sampoid,
  tyyppi = :urakkatyyppi :: urakkatyyppi
WHERE id = :id;

-- name: paivita-tyyppi-hankkeen-urakoille!
-- Paivittaa annetun tyypin kaikille hankkeen urakoille
UPDATE urakka
SET tyyppi = :urakkatyyppi :: urakkatyyppi
WHERE hanke = (SELECT id
               FROM hanke
               WHERE sampoid = :hanke_sampoid);

-- name: hae-id-sampoidlla
-- Hakee urakan id:n sampo id:llä
SELECT urakka.id
FROM urakka
WHERE sampoid = :sampoid;

-- name: aseta-urakoitsija-sopimuksen-kautta!
-- Asettaa urakalle urakoitsijan sopimuksen Sampo id:n avulla
UPDATE urakka
SET urakoitsija = (
  SELECT id
  FROM organisaatio
  WHERE sampoid = (
    SELECT urakoitsija_sampoid
    FROM sopimus
    WHERE sampoid = :sopimus_sampoid))
WHERE sampoid = (
  SELECT urakka_sampoid
  FROM sopimus
  WHERE sampoid = :sopimus_sampoid AND
        paasopimus IS NULL);

-- name: aseta-urakoitsija-urakoille-yhteyshenkilon-kautta!
-- Asettaa urakoille urakoitsijan yhteyshenkilön Sampo id:n avulla
UPDATE urakka
SET urakoitsija = (
  SELECT id
  FROM organisaatio
  WHERE sampoid = :urakoitsija_sampoid)
WHERE sampoid IN (
  SELECT urakka_sampoid
  FROM sopimus
  WHERE urakoitsija_sampoid = :urakoitsija_sampoid AND
        paasopimus IS NULL);

-- name: hae-yksittainen-urakka
-- Hakee yhden urakan id:n avulla
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
WHERE u.id = :urakka_id;

-- name: paivita-urakka-alaueiden-nakyma!
-- Päivittää urakka-alueiden materialisoidun näkymän
REFRESH MATERIALIZED VIEW urakoiden_alueet;

-- name: hae-urakan-alueurakkanumero
-- Hakee urakan alueurakkanumeron
SELECT alueurakkanro FROM hanke WHERE id = (SELECT hanke FROM urakka WHERE id = :id)
