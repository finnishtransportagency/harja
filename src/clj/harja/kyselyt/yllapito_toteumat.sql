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
  ypk.id,
  kohdenumero,
  nimi,
  urakka,
  sopimus,
  tr_numero                      AS "tr-numero",
  tr_alkuosa                     AS "tr-alkuosa",
  tr_alkuetaisyys                AS "tr-alkuetaisyys",
  tr_loppuosa                    AS "tr-loppuosa",
  tr_loppuetaisyys               AS "tr-loppuetaisyys",
  tr_ajorata                     AS "tr-ajorata",
  tr_kaista                      AS "tr-kaista",
  hinta,
  hinta_kohteelle                AS "hinta-kohteelle",
  hintatyyppi,
  muutospvm,
  yllapitoluokka
FROM yllapitokohde ypk
  LEFT JOIN yllapitokohde_tiemerkinta yt ON yt.yllapitokohde = ypk.id
WHERE
  suorittava_tiemerkintaurakka = :suorittava_tiemerkintaurakka
  AND poistettu IS NOT TRUE;

-- name: paivita-tiemerkintaurakan-yksikkohintainen-tyo<!
UPDATE yllapitokohde_tiemerkinta SET
  hinta = :hinta,
  hintatyyppi = :hintatyyppi::yllapitokohde_tiemerkinta_hintatyyppi,
  muutospvm = :muutospvm,
  hinta_kohteelle = :hinta_kohteelle
WHERE yllapitokohde = :yllapitokohde;

-- name: luo-tiemerkintaurakan-yksikkohintainen-tyo<!
INSERT INTO yllapitokohde_tiemerkinta(yllapitokohde, hinta, hintatyyppi, muutospvm, hinta_kohteelle) VALUES
  (:yllapitokohde, :hinta, :hintatyyppi::yllapitokohde_tiemerkinta_hintatyyppi, :muutospvm,
  :hinta_kohteelle);

-- name: hae-yllapitokohteen-tiemerkintaurakan-yksikkohintaiset-tyot
SELECT id FROM yllapitokohde_tiemerkinta
WHERE yllapitokohde = :yllapitokohde;