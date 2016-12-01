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
JOIN urakka_laskentakohde lk ON lk.id = yt.laskentakohde
                                AND lk.urakka = yt.urakka
WHERE yt.urakka = :urakka;

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
JOIN urakka_laskentakohde lk ON lk.id = yt.laskentakohde
                                AND lk.urakka = yt.urakka
WHERE yt.urakka = :urakka
      AND yt.id = :id;

-- name: luo-uusi-muu-tyo<!
INSERT INTO yllapito_toteuma
(urakka, selite, pvm, hinta, yllapitoluokka)
VALUES (:urakka, :selite, :pvm, :hinta, :yllapitoluokka);

-- name: paivita-muu-tyo<!
UPDATE yllapito_toteuma
SET
selite = :selite,
pvm = :pvm,
hinta = :hinta,
yllapitoluokka = :yllapitoluokka
WHERE id = :id
        AND urakka = :urakka;

-- name: hae-urakan-laskentakohteet
SELECT id, urakka, nimi FROM urakka_laskentakohde
WHERE urakka = :urakka;