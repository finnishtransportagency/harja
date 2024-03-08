-- name: hae-yllapitokohteet
SELECT
  u.nimi  AS urakka,
  u.id    AS urakka_id,
  (SELECT * FROM laske_tr_osoitteen_pituus(
    ypk.tr_numero, 
    ypk.tr_alkuosa, 
    ypk.tr_alkuetaisyys, 
    ypk.tr_loppuosa,
    ypk.tr_loppuetaisyys)) AS "pituus",
  ypk.id,
  ypk.yhaid,
  ypk.kohdenumero,
  ypk.tunnus,
  ypk.nimi,
  ypk.yotyo,
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
  ypkk.sopimuksen_mukaiset_tyot         AS "sopimuksen-mukaiset-tyot",
  ypkk.maaramuutokset                   AS "maaramuutokset",
  ypkk.arvonvahennykset                 AS "arvonvahennykset",
  ypkk.toteutunut_hinta                 AS "toteutunut-hinta",
  ypkk.bitumi_indeksi                   AS "bitumi-indeksi",
  ypkk.kaasuindeksi,
  ypkk.maku_paallysteet                 AS "maku-paallysteet",
  o.id                                  AS "hallintayksikko_id",
  o.nimi                                AS "hallintayksikko_nimi"
FROM yllapitokohde ypk
  LEFT JOIN laatupoikkeama lp 
	  	 ON (lp.yllapitokohde = ypk.id 
	  	 AND lp.urakka = ypk.urakka AND lp.poistettu IS NOT TRUE)
  LEFT JOIN sanktio s 
	  	 ON s.laatupoikkeama = lp.id 
	  	 AND s.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohteen_kustannukset ypkk ON ypkk.yllapitokohde = ypk.id
  LEFT JOIN urakka u ON ypk.urakka = u.id
  LEFT JOIN organisaatio o ON u.hallintayksikko = o.id
WHERE ypk.vuodet @> ARRAY [:vuosi] :: INT []
      AND ypk.poistettu IS NOT TRUE
      AND ((:urakka::INTEGER IS NULL AND u.urakkanro IS NOT NULL) OR ypk.urakka = :urakka) 
      AND ((:hallintayksikko::INTEGER IS NULL AND u.urakkanro IS NOT NULL) OR (u.id IN (SELECT id
                                                                                        FROM urakka
                                                                                        WHERE hallintayksikko =
                                                                                              :hallintayksikko) AND u.urakkanro IS NOT NULL))
GROUP BY ypk.id, ypkk.sopimuksen_mukaiset_tyot, ypkk.maaramuutokset, ypkk.arvonvahennykset, ypkk.bitumi_indeksi, ypkk.kaasuindeksi,  ypkk.toteutunut_hinta, ypkk.maku_paallysteet, o.id, o.nimi, u.id, u.nimi;

-- name: hae-muut-kustannukset
SELECT
  u.id,
  o.id     AS "hallintayksikko_id",
  o.nimi   AS "hallintayksikko_nimi",
  yt.id,
  yt.pvm,
  yt.selite,
  yt.hinta
FROM yllapito_muu_toteuma yt
  LEFT JOIN urakka u ON u.id = yt.urakka
  LEFT JOIN organisaatio o ON u.hallintayksikko = o.id
WHERE ((:urakka::INTEGER IS NULL AND u.urakkanro IS NOT NULL) OR yt.urakka = :urakka)
	    AND ((:hallintayksikko::INTEGER IS NULL AND u.urakkanro IS NOT NULL) 
      OR yt.urakka IN (SELECT id
                      FROM urakka
                      WHERE hallintayksikko = :hallintayksikko))
      AND (SELECT EXTRACT(YEAR FROM yt.pvm)) = :vuosi
      AND yt.poistettu IS NOT TRUE
ORDER BY yt.pvm DESC;

-- name: hae-yllapitourakan-sanktiot
SELECT
  o.id     AS "hallintayksikko_id",
  o.nimi   AS "hallintayksikko_nimi",
  s.maara,
  s.sakkoryhma,
  lp.aika AS "pvm",
  ypk.nimi AS yllapitokohde_nimi,
  ypk.yhaid AS yllapitokohde_yhaid
FROM sanktio s
  LEFT JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id
  LEFT JOIN yllapitokohde ypk ON ypk.id = lp.yllapitokohde
  LEFT JOIN urakka u ON u.id = lp.urakka
  LEFT JOIN organisaatio o ON u.hallintayksikko = o.id
WHERE ((:urakka::INTEGER IS NULL AND u.urakkanro IS NOT NULL) 
      OR s.laatupoikkeama IN (SELECT id
                              FROM laatupoikkeama lp
                              WHERE lp.urakka = :urakka))
      AND ((:hallintayksikko::INTEGER IS NULL AND u.urakkanro IS NOT NULL) 
      OR u.id IN (SELECT id
                  FROM urakka
                  WHERE hallintayksikko = :hallintayksikko))
      AND (s.sakkoryhma = 'yllapidon_sakko' OR s.sakkoryhma = 'yllapidon_bonus')
      AND (SELECT EXTRACT(YEAR FROM lp.aika)) = :vuosi
      AND s.poistettu IS NOT TRUE
ORDER BY s.perintapvm DESC;

-- name: pkluokkien-kustannukset-hallintayksikoittain
-- Päätellään päällystyskohteen pk-luokka laskemalla alikohteiden (yllapitokohdeosa) pituudet ja ottamalla se
-- pk-luokka, jonka pituus on suurin
  WITH sakot AS (SELECT SUM(-(s.maara)) AS sakot,
                        ypk.id          AS yllapitokohdeid
                   FROM yllapitokohde ypk
                            JOIN urakka u ON ypk.urakka = u.id
                            JOIN organisaatio o ON u.hallintayksikko = o.id
                            LEFT JOIN laatupoikkeama lp ON lp.yllapitokohde = ypk.id AND lp.poistettu IS NOT TRUE
                            LEFT JOIN sanktio s ON s.laatupoikkeama = lp.id AND s.poistettu IS NOT TRUE
                  WHERE :vuosi = ANY (ypk.vuodet)
                    AND (:hallintayksikko::INTEGER IS NULL OR u.hallintayksikko = :hallintayksikko)
                  GROUP BY ypk.id)
SELECT u.nimi                                     AS nimi,
       u.id                                       AS urakka_id,
       o.id                                       AS "hallintayksikko_id",
       LPAD(CAST(o.elynumero AS VARCHAR), 2, '0') AS elynumero,
       o.nimi                                     AS "hallintayksikko_nimi",
       ypk.pkluokka,
       SUM(ypkk.sopimuksen_mukaiset_tyot)         AS "sopimuksen-mukaiset-tyot",
       SUM(ypkk.maaramuutokset)                   AS "maaramuutokset",
       SUM(ypkk.arvonvahennykset)                 AS "arvonvahennykset",
       SUM(ypkk.toteutunut_hinta)                 AS "toteutunut-hinta",
       SUM(ypkk.bitumi_indeksi)                   AS "bitumi-indeksi",
       SUM(ypkk.kaasuindeksi)                     AS "kaasuindeksi",
       SUM(ypkk.maku_paallysteet)                 AS "maku-paallysteet",
       SUM(s.sakot)                               AS "sakot-ja-bonukset"
  FROM yllapitokohde ypk
           LEFT JOIN yllapitokohteen_kustannukset ypkk ON ypk.id = ypkk.yllapitokohde
           LEFT JOIN sakot s ON s.yllapitokohdeid = ypk.id
           JOIN urakka u ON ypk.urakka = u.id
           JOIN organisaatio o ON u.hallintayksikko = o.id
 WHERE :vuosi = ANY (ypk.vuodet)
   AND (:hallintayksikko::INTEGER IS NULL OR u.hallintayksikko = :hallintayksikko)
   AND ypk.poistettu IS NOT TRUE
 GROUP BY o.id, ypk.pkluokka, u.id, u.nimi;


-- name: pkluokkien-yotyot-hallintayksikoittain
SELECT u.nimi                             AS nimi,
       u.id                               AS urakka_id,
       o.id                               AS "hallintayksikko_id",
       o.nimi                             AS "hallintayksikko_nimi",
       ypk.yotyo,
       ypk.pkluokka,
       SUM((SELECT *
              FROM laske_tr_osoitteen_pituus(
                  ypk.tr_numero,
                  ypk.tr_alkuosa,
                  ypk.tr_alkuetaisyys,
                  ypk.tr_loppuosa,
                  ypk.tr_loppuetaisyys))) AS "pituus",
       10                                 AS prosentti
  FROM yllapitokohde ypk
           LEFT JOIN yllapitokohteen_kustannukset ypkk ON ypk.id = ypkk.yllapitokohde
           JOIN urakka u ON ypk.urakka = u.id
           JOIN organisaatio o ON u.hallintayksikko = o.id
 WHERE :vuosi = ANY (ypk.vuodet)
   AND (:hallintayksikko::INTEGER IS NULL OR u.hallintayksikko = :hallintayksikko)
   AND ypk.poistettu IS NOT TRUE
 GROUP BY o.id, ypk.pkluokka, ypk.yotyo, u.id
 ORDER BY o.id ASC;
