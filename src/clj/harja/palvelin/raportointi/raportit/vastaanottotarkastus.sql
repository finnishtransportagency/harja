-- name: hae-yllapitokohteet
SELECT
  ypk.id,
  ypk.yhaid,
  ypk.kohdenumero,
  ypk.tunnus,
  ypk.nimi,
  ypk.yllapitokohdetyotyyppi,
  ypk.tr_numero                         AS "tr-numero",
  ypk.tr_ajorata                        AS "tr-ajorata",
  ypk.tr_kaista                         AS "tr-kaista",
  ypk.tr_alkuosa                        AS "tr-alkuosa",
  ypk.tr_alkuetaisyys                   AS "tr-alkuetaisyys",
  ypk.tr_loppuosa                       AS "tr-loppuosa",
  ypk.tr_loppuetaisyys                  AS "tr-loppuetaisyys",
  ypk.keskimaarainen_vuorokausiliikenne AS "kvl",
  ypk.yllapitoluokka                    AS "yplk",
  sum(-s.maara)                         AS "sakot-ja-bonukset",
  ypk.sopimuksen_mukaiset_tyot          AS "sopimuksen-mukaiset-tyot",
  ypk.arvonvahennykset                  AS "arvonmuutokset",
  ypk.toteutunut_hinta                  AS "toteutunut-hinta",
  ypk.bitumi_indeksi                    AS "bitumi-indeksi",
  ypk.kaasuindeksi
FROM yllapitokohde ypk
  LEFT JOIN laatupoikkeama lp ON (lp.yllapitokohde = ypk.id AND lp.urakka = ypk.urakka AND lp.poistettu IS NOT TRUE)
  LEFT JOIN sanktio s ON s.laatupoikkeama = lp.id AND s.poistettu IS NOT TRUE
WHERE ypk.urakka = :urakka
AND ypk.poistettu IS NOT TRUE
GROUP BY ypk.id;

-- name: hae-muut-kustannukset
SELECT
  yt.id,
  yt.pvm,
  yt.selite,
  yt.hinta
FROM yllapito_muu_toteuma yt
WHERE yt.urakka = :urakka
      AND yt.poistettu IS NOT TRUE
ORDER BY yt.pvm DESC;