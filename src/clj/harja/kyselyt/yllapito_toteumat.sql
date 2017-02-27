-- name: hae-muut-tyot
SELECT
  yt.id,
  yt.urakka,
  yt.selite,
  yt.pvm,
  yt.hinta,
  yt.yllapitoluokka,
  lk.id as "laskentakohde-id",
  lk.nimi as "laskentakohde-nimi"
FROM yllapito_toteuma yt
  LEFT JOIN urakka_laskentakohde lk ON lk.id = yt.laskentakohde
WHERE yt.urakka = :urakka AND yt.sopimus = :sopimus
      AND yt.pvm::DATE BETWEEN :alkupvm and :loppupvm
ORDER BY yt.pvm DESC;

-- name: hae-muu-tyo
SELECT
  yt.id,
  yt.urakka,
  yt.selite,
  yt.pvm,
  yt.hinta,
  yt.yllapitoluokka,
  lk.id as "laskentakohde-id",
  lk.nimi as "laskentakohde-nimi"
FROM yllapito_toteuma yt
  LEFT JOIN urakka_laskentakohde lk ON lk.id = yt.laskentakohde
WHERE yt.urakka = :urakka
      AND yt.id = :id;

-- name: luo-uusi-muu-tyo<!
INSERT INTO yllapito_toteuma
(urakka, sopimus, selite, pvm, hinta, yllapitoluokka, laskentakohde, luotu, luoja)
VALUES (:urakka, :sopimus, :selite, :pvm, :hinta, :yllapitoluokka, :laskentakohde, NOW(), :kayttaja);

-- name: paivita-muu-tyo<!
UPDATE yllapito_toteuma
SET
  selite = :selite,
  sopimus = :sopimus,
  pvm = :pvm,
  hinta = :hinta,
  yllapitoluokka = :yllapitoluokka,
  laskentakohde = :laskentakohde,
  muokattu = NOW(),
  muokkaaja = :kayttaja
WHERE id = :id and urakka = :urakka;

-- name: hae-urakan-laskentakohteet
SELECT id, urakka, nimi FROM urakka_laskentakohde
WHERE urakka = :urakka;

-- name: luo-uusi-urakan_laskentakohde<!
INSERT INTO urakka_laskentakohde
(urakka, nimi, luotu, luoja)
VALUES (:urakka, :nimi, NOW(), :kayttaja);

-- name: hae-tiemerkintaurakan-yksikkohintaiset-tyot
SELECT
  id,
  yllapitokohde                  AS "yllapitokohde-id",
  hinta,
  hintatyyppi,
  hinta_kohteelle                AS "hinta-kohteelle",
  muutospvm,
  yllapitoluokka,
  selite,
  tr_numero                      AS "tr-numero",
  pituus
FROM tiemerkinnan_yksikkohintainen_toteuma tyt
WHERE
  poistettu IS NOT TRUE
  AND urakka = :urakka
  AND ((yllapitokohde IS NULL)
      OR
      (yllapitokohde IS NOT NULL
      AND
      (SELECT poistettu FROM yllapitokohde WHERE id = tyt.yllapitokohde) IS NOT TRUE));

-- name: paivita-tiemerkintaurakan-yksikkohintainen-tyo<!
UPDATE tiemerkinnan_yksikkohintainen_toteuma SET
  yllapitokohde = :yllapitokohde,
  hinta = :hinta,
  hintatyyppi = :hintatyyppi::tiemerkinta_toteuma_hintatyyppi,
  muutospvm = :muutospvm,
  hinta_kohteelle = :hinta_kohteelle,
  selite = :selite,
  tr_numero = :tr_numero,
  yllapitoluokka = :yllapitoluokka,
  pituus = :pituus,
  poistettu = :poistettu
WHERE id = :id
AND ((yllapitokohde IS NULL
    OR
    (SELECT suorittava_tiemerkintaurakka FROM yllapitokohde WHERE id = yllapitokohde) = :urakka));

-- name: luo-tiemerkintaurakan-yksikkohintainen-tyo<!
INSERT INTO tiemerkinnan_yksikkohintainen_toteuma
(yllapitokohde, urakka, hinta, hintatyyppi, muutospvm, hinta_kohteelle, selite,
tr_numero, yllapitoluokka, pituus)
VALUES (:yllapitokohde, :urakka, :hinta, :hintatyyppi::tiemerkinta_toteuma_hintatyyppi, :muutospvm,
  :hinta_kohteelle, :selite, :tr_numero, :yllapitoluokka, :pituus);

-- name: hae-yllapitokohteen-tiemerkintaurakan-yksikkohintaiset-tyot
SELECT id FROM tiemerkinnan_yksikkohintainen_toteuma
WHERE yllapitokohde = :yllapitokohde;