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
      AND yt.pvm::DATE BETWEEN :alkupvm and :loppupvm;

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
WHERE id = :id;

-- name: hae-urakan-laskentakohteet
SELECT id, urakka, nimi FROM urakka_laskentakohde
WHERE urakka = :urakka;

-- name: luo-uusi-urakan_laskentakohde<!
INSERT INTO urakka_laskentakohde
(urakka, nimi, luotu, luoja)
VALUES (:urakka, :nimi, NOW(), :kayttaja);
