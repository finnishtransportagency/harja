-- name: listaa-urakoitsijat
-- Hakee kaikkien väylämuotojen ja urakkatyyppien urakoitsijat
SELECT
  u.tyyppi AS urakkatyyppi,
  u.nimi AS urakka_nimi,
  u.id AS urakka_id,
  u.alkupvm AS urakka_alkupvm,
  u.loppupvm AS urakka_loppupvm,
  y.id,
  y.ytunnus,
  y.nimi,
  y.katuosoite,
  y.postinumero
FROM urakka u
  LEFT JOIN organisaatio y ON u.urakoitsija = y.id
WHERE y.tyyppi = 'urakoitsija' :: ORGANISAATIOTYYPPI;

-- name: hae-urakkatyypin-urakoitsijat
-- Palauttaa annetun urakkatyypin urakoitsijoiden id:t
SELECT y.id
  FROM urakka u
  		LEFT JOIN organisaatio y ON u.urakoitsija = y.id
 WHERE u.tyyppi = :tyyppi::urakkatyyppi;

-- name: hae-yllapidon-urakoitsijat
-- Palauttaa kaikkien ylläpitourakkatyyppien urakoitsijoiden id:t
SELECT y.id
  FROM urakka u
  		LEFT JOIN organisaatio y ON u.urakoitsija = y.id
 WHERE u.tyyppi IN ('paallystys', 'paikkaus', 'valaistus', 'tiemerkinta', 'siltakorjaus', 'tekniset-laitteet');

-- name: hae-vesivayla-urakoitsijat
SELECT
  urk.id,
  urk.nimi,
  urk.ytunnus,
  urk.katuosoite,
  urk.postinumero,
  u.nimi AS urakka_nimi,
  u.id AS urakka_id,
  u.alkupvm AS urakka_alkupvm,
  u.loppupvm AS urakka_loppupvm
FROM organisaatio urk
  LEFT JOIN urakka u ON urk.id = u.urakoitsija
WHERE urk.tyyppi = 'urakoitsija'
      AND urk.poistettu IS NOT TRUE
      AND (u.tyyppi IN ('vesivayla-hoito', 'vesivayla-ruoppaus', 'vesivayla-turvalaitteiden-korjaus', 'vesivayla-kanavien-hoito', 'vesivayla-kanavien-korjaus')
      OR urk.harjassa_luotu IS TRUE)
ORDER BY urk.nimi;

-- name: luo-urakoitsija<!
INSERT INTO organisaatio (nimi, ytunnus, katuosoite, postinumero, tyyppi, luoja, luotu, harjassa_luotu)
    VALUES (:nimi, :ytunnus, :katuosoite, :postinumero,
            'urakoitsija', :kayttaja, NOW(), TRUE);

-- name: paivita-urakoitsija<!
UPDATE organisaatio SET
  nimi = :nimi,
  ytunnus = :ytunnus,
  katuosoite = :katuosoite,
  postinumero = :postinumero,
  muokkaaja = :kayttaja,
  muokattu = NOW()
WHERE id = :id;