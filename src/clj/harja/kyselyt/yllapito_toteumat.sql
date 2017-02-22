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
  yllapitokohde,
  hinta,
    hintatyyppi,
  hinta_kohteelle                AS "hinta-kohteelle",
  muutospvm,
  yllapitoluokka
  kohde_nimi,
  tr_numero                      AS "tr-numero",
  pituus
FROM tiemerkinnan_yksikkohintainen_toteuma yt ON yt.yllapitokohde = ypk.id
WHERE
  suorittava_tiemerkintaurakka = :suorittava_tiemerkintaurakka
  AND poistettu IS NOT TRUE;

-- name: paivita-tiemerkintaurakan-yksikkohintainen-tyo<!
UPDATE tiemerkinnan_yksikkohintainen_toteuma SET
  hinta = :hinta,
  hintatyyppi = :hintatyyppi::tiemerkinta_toteuma_hintatyyppi,
  muutospvm = :muutospvm,
  hinta_kohteelle = :hinta_kohteelle
WHERE yllapitokohde = :yllapitokohde;

-- name: luo-tiemerkintaurakan-yksikkohintainen-tyo<!
INSERT INTO tiemerkinnan_yksikkohintainen_toteuma(yllapitokohde, hinta, hintatyyppi, muutospvm, hinta_kohteelle) VALUES
  (:yllapitokohde, :hinta, :hintatyyppi::tiemerkinta_toteuma_hintatyyppi, :muutospvm,
  :hinta_kohteelle);

-- name: hae-yllapitokohteen-tiemerkintaurakan-yksikkohintaiset-tyot
SELECT id FROM tiemerkinnan_yksikkohintainen_toteuma
WHERE yllapitokohde = :yllapitokohde;