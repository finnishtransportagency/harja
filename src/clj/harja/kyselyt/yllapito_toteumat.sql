-- name: hae-muut-tyot
SELECT
  id,
  urakka,
  selite,
  pvm,
  hinta,
  yllapitoluokka,
  lk.id as "laskentakohde-id",
  lk.nimi as "laskentakohde-nimi"
FROM yllapito_toteuma yt
JOIN urakka_laskentakohde lk ON lk.id = yt.laskentakohde
                                AND lk.urakka = yt.urakka
WHERE urakka = :urakka