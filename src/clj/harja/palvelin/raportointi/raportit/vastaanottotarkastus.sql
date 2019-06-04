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
  ypkk.sopimuksen_mukaiset_tyot          AS "sopimuksen-mukaiset-tyot",
  ypkk.arvonvahennykset                  AS "arvonvahennykset",
  ypkk.toteutunut_hinta                  AS "toteutunut-hinta",
  ypkk.bitumi_indeksi                    AS "bitumi-indeksi",
  ypkk.kaasuindeksi
FROM yllapitokohde ypk
  LEFT JOIN laatupoikkeama lp ON (lp.yllapitokohde = ypk.id AND lp.urakka = ypk.urakka AND lp.poistettu IS NOT TRUE)
  LEFT JOIN sanktio s ON s.laatupoikkeama = lp.id AND s.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohteen_kustannukset ypkk ON ypkk.yllapitokohde = ypk.id
WHERE ypk.urakka = :urakka
      AND ypk.vuodet @> ARRAY [:vuosi] :: INT []
      AND ypk.poistettu IS NOT TRUE
GROUP BY ypk.id, ypkk.sopimuksen_mukaiset_tyot, ypkk.arvonvahennykset, ypkk.bitumi_indeksi, ypkk.kaasuindeksi,  ypkk.toteutunut_hinta;

-- name: hae-muut-kustannukset
SELECT
  yt.id,
  yt.pvm,
  yt.selite,
  yt.hinta
FROM yllapito_muu_toteuma yt
WHERE yt.urakka = :urakka
      AND (SELECT EXTRACT(YEAR FROM yt.pvm)) = :vuosi
      AND yt.poistettu IS NOT TRUE
ORDER BY yt.pvm DESC;

-- name: hae-kohteisiin-kuulumattomat-sanktiot
SELECT
  s.maara,
  s.sakkoryhma,
  lp.aika AS "pvm"
FROM sanktio s
  LEFT JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id
WHERE s.laatupoikkeama IN (SELECT id
                           FROM laatupoikkeama lp
                           WHERE lp.urakka = :urakka AND lp.yllapitokohde IS NULL)
      AND (s.sakkoryhma = 'yllapidon_sakko' OR s.sakkoryhma = 'yllapidon_bonus')
      AND (SELECT EXTRACT(YEAR FROM lp.aika)) = :vuosi
      AND s.poistettu IS NOT TRUE
ORDER BY s.perintapvm DESC;
