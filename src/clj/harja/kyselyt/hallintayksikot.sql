-- name: listaa-hallintayksikot-kulkumuodolle
-- Hakee hallintayksiköiden perustiedot ja geometriat kulkumuodon mukaan
SELECT
  id,
  nimi,
  alue,
  liikennemuoto,
  lpad(cast(elynumero AS VARCHAR), 2, '0') AS elynumero
FROM organisaatio
WHERE tyyppi = 'hallintayksikko' :: ORGANISAATIOTYYPPI AND
      (:liikennemuoto::CHARACTER IS NULL OR liikennemuoto = :liikennemuoto :: LIIKENNEMUOTO)
ORDER BY elynumero ASC, nimi ASC;

-- name: listaa-elinvoimakeskukset-kulkumuodolle
SELECT
    id,
    nimi,
    alue,
    liikennemuoto,
    lpad(cast(elinvoimakeskusnumero AS VARCHAR), 2, '0') AS evknumero
FROM organisaatio
WHERE tyyppi = 'elinvoimakeskus' :: ORGANISAATIOTYYPPI AND
    (:liikennemuoto::CHARACTER IS NULL OR liikennemuoto = :liikennemuoto :: LIIKENNEMUOTO)
ORDER BY elinvoimakeskusnumero ASC, nimi ASC;

-- name: hae-organisaation-tunnistetiedot
-- Hakee organisaation perustiedot tekstihaulla.
SELECT o.id, o.nimi, o.tyyppi as organisaatiotyyppi, o.lyhenne
  FROM organisaatio o
 WHERE o.nimi ILIKE :teksti OR upper(o.lyhenne) ILIKE upper(:teksti)
 LIMIT 11;

-- name: hae-organisaatio
-- Hakee organisaation perustiedot id:llä
SELECT o.id, o.nimi, o.tyyppi as organisaatiotyyppi
  FROM organisaatio o
 WHERE o.id = :id;

-- name: hae-hallintayksikon-geometria
SELECT alue FROM organisaatio WHERE id = :id;

-- name: hallintayksikot-ilman-geometriaa
select id, nimi from organisaatio where tyyppi = 'hallintayksikko'::ORGANISAATIOTYYPPI;
