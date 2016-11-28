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
WHERE yt.urakka = :urakka