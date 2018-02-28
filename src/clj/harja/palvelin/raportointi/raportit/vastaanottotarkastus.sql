-- name: hae-yllapitokohteet
SELECT
  id,
  kohdenumero,
  tunnus,
  nimi,
  tr_numero                         AS "tr-numero",
  tr_ajorata                        AS "tr-ajorata",
  tr_kaista                         AS "tr-kaista",
  tr_alkuosa                        AS "tr-alkuosa",
  tr_alkuetaisyys                   AS "tr-alkuetaisyys",
  tr_loppuosa                       AS "tr-loppuosa",
  tr_loppuetaisyys                  AS "tr-loppuetaisyys",
  keskimaarainen_vuorokausiliikenne AS "kvl",
  yllapitoluokka                    AS "yplk",
  sopimuksen_mukaiset_tyot          AS "tarjoushinta",
  arvonvahennykset                  AS "arvonmuutokset",
  bitumi_indeksi                    AS "bitumi-indeksi",
  kaasuindeksi
FROM yllapitokohde ypk
WHERE ypk.urakka = :urakka
AND poistettu IS NOT TRUE;