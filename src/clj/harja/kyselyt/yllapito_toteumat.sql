-- name: hae-muut-tyot
SELECT
  yt.id,
  yt.urakka,
  yt.selite,
  yt.pvm,
  yt.hinta,
  yt.tyyppi,
  yt.yllapitoluokka,
  lk.id   AS "laskentakohde-id",
  lk.nimi AS "laskentakohde-nimi"
FROM yllapito_muu_toteuma yt
  LEFT JOIN urakka_laskentakohde lk ON lk.id = yt.laskentakohde
WHERE yt.urakka = :urakka AND yt.sopimus = :sopimus
      AND yt.pvm :: DATE BETWEEN :alkupvm AND :loppupvm
      AND yt.poistettu IS NOT TRUE
ORDER BY yt.pvm DESC;

-- name: hae-muu-tyo
SELECT
  yt.id,
  yt.urakka,
  yt.selite,
  yt.pvm,
  yt.hinta,
  yt.tyyppi,
  yt.yllapitoluokka,
  lk.id   AS "laskentakohde-id",
  lk.nimi AS "laskentakohde-nimi"
FROM yllapito_muu_toteuma yt
  LEFT JOIN urakka_laskentakohde lk ON lk.id = yt.laskentakohde
WHERE yt.urakka = :urakka
      AND yt.id = :id
      AND yt.poistettu IS NOT TRUE;

-- name: luo-uusi-muu-tyo<!
INSERT INTO yllapito_muu_toteuma
  (urakka, sopimus, selite, pvm, hinta, tyyppi, yllapitoluokka, laskentakohde, luotu, luoja)
VALUES
  (:urakka, :sopimus, :selite, :pvm, :hinta, :tyyppi::yllapito_muu_toteuma_tyyppi, :yllapitoluokka, :laskentakohde, NOW(), :kayttaja);

-- name: paivita-muu-tyo<!
UPDATE yllapito_muu_toteuma
SET
  selite         = :selite,
  sopimus        = :sopimus,
  pvm            = :pvm,
  hinta          = :hinta,
  tyyppi         = :tyyppi::yllapito_muu_toteuma_tyyppi,
  yllapitoluokka = :yllapitoluokka,
  laskentakohde  = :laskentakohde,
  muokattu       = NOW(),
  muokkaaja      = :kayttaja,
  poistettu      = :poistettu
WHERE id = :id AND urakka = :urakka
      AND poistettu IS NOT TRUE;

-- name: hae-urakan-laskentakohteet
SELECT
  id,
  urakka,
  nimi
FROM urakka_laskentakohde
WHERE urakka = :urakka;

-- name: luo-uusi-urakan_laskentakohde<!
INSERT INTO urakka_laskentakohde
(urakka, nimi, luotu, luoja)
VALUES (:urakka, :nimi, NOW(), :kayttaja);

-- name: hae-tiemerkintaurakan-yksikkohintaiset-tyot
SELECT
  id,
  yllapitokohde   AS "yllapitokohde-id",
  hinta,
  hintatyyppi,
  hinta_kohteelle AS "hinta-kohteelle",
  paivamaara,
  yllapitoluokka,
  selite,
  tr_numero       AS "tr-numero",
  pituus
FROM tiemerkinnan_yksikkohintainen_toteuma tyt
WHERE
  poistettu IS NOT TRUE
  AND urakka = :urakka
  AND ((yllapitokohde IS NULL)
       OR
       (yllapitokohde IS NOT NULL
        AND
        (SELECT poistettu
         FROM yllapitokohde
         WHERE id = tyt.yllapitokohde) IS NOT TRUE));

-- name: paivita-tiemerkintaurakan-yksikkohintainen-tyo<!
UPDATE tiemerkinnan_yksikkohintainen_toteuma
SET
  yllapitokohde   = :yllapitokohde,
  hinta           = :hinta,
  hintatyyppi     = :hintatyyppi :: tiemerkinta_toteuma_hintatyyppi,
  paivamaara      = :paivamaara,
  hinta_kohteelle = :hinta_kohteelle,
  selite          = :selite,
  tr_numero       = :tr_numero,
  yllapitoluokka  = :yllapitoluokka,
  pituus          = :pituus,
  poistettu       = :poistettu
WHERE id = :id
      AND ((yllapitokohde IS NULL
            OR
            (SELECT suorittava_tiemerkintaurakka
             FROM yllapitokohde
             WHERE id = yllapitokohde) = :urakka));

-- name: luo-tiemerkintaurakan-yksikkohintainen-tyo<!
INSERT INTO tiemerkinnan_yksikkohintainen_toteuma
(yllapitokohde, urakka, hinta, hintatyyppi, paivamaara, hinta_kohteelle, selite,
 tr_numero, yllapitoluokka, pituus, luoja, ulkoinen_id)
VALUES (:yllapitokohde, :urakka, :hinta, :hintatyyppi :: tiemerkinta_toteuma_hintatyyppi, :paivamaara,
                        :hinta_kohteelle, :selite, :tr_numero, :yllapitoluokka, :pituus, :luoja, :ulkoinen_id);

-- name: hae-yllapitokohteen-tiemerkintaurakan-yksikkohintaiset-tyot
SELECT id
FROM tiemerkinnan_yksikkohintainen_toteuma
WHERE yllapitokohde = :yllapitokohde;

-- name: hae-tiemerkintatoteuman-id-ulkoisella-idlla
-- single?: true
SELECT id
FROM tiemerkinnan_yksikkohintainen_toteuma
WHERE luoja = :luoja AND ulkoinen_id = :ulkoinen_id;

-- name: poista-tiemerkintatoteumat-ulkoisilla-idlla!
UPDATE tiemerkinnan_yksikkohintainen_toteuma
SET poistettu = TRUE
WHERE poistettu IS NOT TRUE AND
      ulkoinen_id = ANY (:ulkoiset_idt :: INT []) AND
      luoja = :luoja_id AND
      (:urakka_id :: INT IS NULL OR urakka = :urakka_id) AND
      (:yllapitokohde_id :: INT IS NULL OR yllapitokohde = :yllapitokohde_id);

-- name: muun-toteuman-urakka
SELECT t.urakka
FROM yllapito_muu_toteuma t
WHERE t.id = :toteuma;