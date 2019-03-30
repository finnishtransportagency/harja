-- name: listaa-urakoitsijat
-- Hakee kaikkien väylämuotojen ja urakkatyyppien urakoitsijat
SELECT y.id, y.ytunnus, y.nimi,
       u.tyyppi as urakkatyyppi
  FROM urakka u
       LEFT JOIN organisaatio y ON u.urakoitsija = y.id
 WHERE y.tyyppi = 'urakoitsija'::organisaatiotyyppi;

-- name: hae-urakoitsijat-urakkatietoineen
SELECT
  urk.id,
  urk.nimi,
  urk.ytunnus,
  urk.katuosoite,
  urk.postinumero,
  urk.postitoimipaikka,
  u.nimi AS urakka_nimi,
  u.id AS urakka_id,
  u.alkupvm AS urakka_alkupvm,
  u.loppupvm AS urakka_loppupvm
FROM organisaatio urk
LEFT JOIN urakka u ON urk.id = u.urakoitsija
WHERE urk.tyyppi = 'urakoitsija'::organisaatiotyyppi;

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

-- name: hae-vesivaylaurakoitsijat
SELECT
  urk.id,
  urk.nimi,
  urk.ytunnus,
  urk.katuosoite,
  urk.postinumero,
  urk.postitoimipaikka,
  urk.harjassa_luotu AS "harjassa-luotu?",
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
INSERT INTO organisaatio (nimi, ytunnus, katuosoite, postinumero, postitoimipaikka, tyyppi, luoja, luotu, harjassa_luotu)
    VALUES (:nimi, :ytunnus, :katuosoite, :postinumero, :postitoimipaikka,
            'urakoitsija', :kayttaja, NOW(), TRUE);

-- name: paivita-urakoitsija<!
UPDATE organisaatio SET
  nimi = :nimi,
  ytunnus = :ytunnus,
  katuosoite = :katuosoite,
  postinumero = :postinumero,
  postitoimipaikka = :postitoimipaikka,
  muokkaaja = :kayttaja,
  muokattu = NOW()
WHERE id = :id;
