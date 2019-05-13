-- name: hae-kaikki-urakan-yllapitokohteet
-- Hakee urakan kaikki yllapitokohteet, myös tiemerkinnän kautta suoritettavat,
-- sekä niiden päällystysilmoitukset ja alikohteet
SELECT
  ypk.id,
  ypk.urakka,
  ypk.sopimus,
  ypk.kohdenumero,
  ypk.nimi,
  ypk.tunnus,
  ypk.yllapitokohdetyyppi,
  ypk.yllapitokohdetyotyyppi,
  ypkk.sopimuksen_mukaiset_tyot          AS "sopimuksen-mukaiset-tyot",
  ypkk.arvonvahennykset,
  ypkk.bitumi_indeksi                    AS "bitumi-indeksi",
  ypkk.kaasuindeksi,
  ypk.nykyinen_paallyste                AS "nykyinen-paallyste",
  ypk.keskimaarainen_vuorokausiliikenne AS "keskimaarainen-vuorokausiliikenne",
  ypk.yllapitoluokka,
  ypk.tr_numero                         AS "tr-numero",
  ypk.tr_alkuosa                        AS "tr-alkuosa",
  ypk.tr_alkuetaisyys                   AS "tr-alkuetaisyys",
  ypk.tr_loppuosa                       AS "tr-loppuosa",
  ypk.tr_loppuetaisyys                  AS "tr-loppuetaisyys",
  ypk.tr_ajorata                        AS "tr-ajorata",
  ypk.tr_kaista                         AS "tr-kaista",
  ypka.kohde_alku                       AS "kohde-alku",
  ypka.paallystys_alku                  AS "paallystys-alku",
  ypka.paallystys_loppu                 AS "paallystys-loppu",
  ypka.valmis_tiemerkintaan             AS "valmis-tiemerkintaan",
  ypka.tiemerkinta_takaraja             AS "tiemerkinta-takaraja",
  ypka.tiemerkinta_alku                 AS "tiemerkinta-alku",
  ypka.tiemerkinta_loppu                AS "tiemerkinta-loppu",
  ypka.kohde_valmis                     AS "kohde-valmis",
  ypk.yhaid,
  ypko.id                               AS "kohdeosa_id",
  ypko.yllapitokohde                    AS "kohdeosa_yllapitokohde",
  ypko.nimi                             AS "kohdeosa_nimi",
  ypko.tr_numero                        AS "kohdeosa_tr-numero",
  ypko.tr_alkuosa                       AS "kohdeosa_tr-alkuosa",
  ypko.tr_alkuetaisyys                  AS "kohdeosa_tr-alkuetaisyys",
  ypko.tr_loppuosa                      AS "kohdeosa_tr-loppuosa",
  ypko.tr_loppuetaisyys                 AS "kohdeosa_tr-loppuetaisyys",
  ypko.tr_ajorata                       AS "kohdeosa_tr-ajorata",
  ypko.tr_kaista                        AS "kohdeosa_tr-kaista",
  ypko.poistettu                        AS "kohdeosa_poistettu",
  ypko.sijainti                         AS "kohdeosa_sijainti",
  ypko.yhaid                            AS "kohdeosa_yhaid",
  ypko.paallystetyyppi                  AS "kohdeosa_paallystetyyppi",
  ypko.raekoko                          AS "kohdeosa_raekoko",
  ypko.tyomenetelma                     AS "kohdeosa_tyomenetelma",
  ypko.massamaara                       AS "kohdeosa_massamaara",
  ypko.toimenpide                       AS "kohdeosa_toimenpide",
  st_asgeojson(ypko.sijainti)           AS "kohdeosa_geometria",
  pi.takuupvm                           AS "paallystysilmoitus_takuupvm"
FROM yllapitokohde ypk
  LEFT JOIN yllapitokohdeosa ypko ON ypk.id = ypko.yllapitokohde AND ypko.poistettu IS NOT TRUE
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = ypk.id AND pi.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
  LEFT JOIN yllapitokohteen_kustannukset ypkk ON ypkk.yllapitokohde = ypk.id
WHERE
  (ypk.urakka = :urakka OR ypk.suorittava_tiemerkintaurakka = :urakka)
  AND ypk.poistettu IS NOT TRUE;

-- name: hae-urakkaan-kuuluvat-yllapitokohteet
SELECT ypk.id
FROM yllapitokohde ypk
WHERE
  ypk.urakka = :urakka
  AND ypk.poistettu IS NOT TRUE;

-- name: hae-urakkaan-liittyvat-tiemerkintakohteet
-- Hakee ylläpitokohteet, joihin on merkitty suorittajaksi kyseinen urakka
SELECT ypk.id
FROM yllapitokohde ypk
WHERE
  ypk.suorittava_tiemerkintaurakka = :urakka
  AND ypk.poistettu IS NOT TRUE;

-- name: yllapitokohteen-saa-poistaa
SELECT EXISTS(SELECT *
              FROM yllapitokohde
              WHERE id = :id AND (yhaid IS NULL OR muokattu IS NULL)) AND
       NOT (EXISTS(SELECT *
                   FROM tiemerkinnan_yksikkohintainen_toteuma tyt
                   WHERE yllapitokohde = :id AND tyt.poistettu IS NOT TRUE) OR
            EXISTS(SELECT *
                   FROM sanktio s
                   WHERE poistettu IS NOT TRUE AND laatupoikkeama IN
                                                   (SELECT id
                                                    FROM laatupoikkeama lp
                                                    WHERE yllapitokohde = :id AND lp.poistettu IS NOT TRUE)) OR
            EXISTS(SELECT *
                   FROM paallystysilmoitus pi
                   WHERE paallystyskohde = :id AND pi.poistettu IS NOT TRUE) OR
            EXISTS(SELECT *
                   FROM paikkausilmoitus pai
                   WHERE paikkauskohde = :id AND pai.poistettu IS NOT TRUE) OR
            EXISTS(SELECT *
                   FROM tietyomaa ttm
                   WHERE yllapitokohde = :id) OR
            EXISTS(SELECT *
                   FROM laatupoikkeama lp
                   WHERE yllapitokohde = :id AND lp.poistettu IS NOT TRUE) OR
            EXISTS(SELECT *
                   FROM tarkastus t
                   WHERE yllapitokohde = :id AND t.poistettu IS NOT TRUE)) AS "saa-poistaa"
FROM yllapitokohde
WHERE id = :id;

-- name: kohteen-yhaid
SELECT yhaid
FROM yllapitokohde
WHERE id=:kohde-id AND urakka=:urakka-id

-- name: paallystyskohteen-saa-poistaa
WITH tama_kohde AS (
   SELECT *
   FROM yllapitokohde
   WHERE id = :id
)
SELECT EXISTS(SELECT *
              FROM tama_kohde) AS "yllapitokohde-ei-olemassa",
       EXISTS(SELECT *
              FROM tama_kohde
              WHERE lahetys_onnistunut IS NOT TRUE) AS "yllapitokohde-lahetetty",
       EXISTS(SELECT *
              FROM tama_kohde
              WHERE yhaid IS NOT NULL) AS "yllapitokohde-ei-yhassa",
       NOT (EXISTS(SELECT *
                   FROM tiemerkinnan_yksikkohintainen_toteuma tyt
                   WHERE yllapitokohde = :id AND tyt.poistettu IS NOT TRUE)) AS "tiemerkinnant-yh-toteuma",
       NOT (EXISTS(SELECT *
                   FROM sanktio s
                   WHERE poistettu IS NOT TRUE AND laatupoikkeama IN
                                                   (SELECT id
                                                    FROM laatupoikkeama lp
                                                    WHERE yllapitokohde = :id AND lp.poistettu IS NOT TRUE))) AS "sanktio",
       NOT (EXISTS(SELECT *
                   FROM paallystysilmoitus pi
                   WHERE paallystyskohde = :id AND pi.poistettu IS NOT TRUE)) AS "paallystysilmoitus",
       NOT (EXISTS(SELECT *
                   FROM tietyomaa ttm
                   WHERE yllapitokohde = :id) AS tietyomaa,
       NOT (EXISTS(SELECT *
                   FROM laatupoikkeama lp
                   WHERE yllapitokohde = :id AND lp.poistettu IS NOT TRUE)) AS laatupoikkeama,
       NOT (EXISTS(SELECT *
                   FROM tarkastus t
                   WHERE yllapitokohde = :id AND t.poistettu IS NOT TRUE))) AS "tarkastus"
FROM yllapitokohde
WHERE id = :id;

-- name: yllapitokohteet-joille-linkityksia
-- Palauttaa ne ylläpitokohteiden idt annetusta id joukosta, joille on tehty jotain linkityksiä,
-- kuten laatupoikkeamia tai ilmoituksia.
SELECT tyt.yllapitokohde
FROM tiemerkinnan_yksikkohintainen_toteuma tyt
WHERE tyt.yllapitokohde IN (:idt) AND tyt.poistettu IS NOT TRUE
UNION
SELECT lp.yllapitokohde
FROM laatupoikkeama lp
WHERE lp.yllapitokohde IN (:idt) AND lp.poistettu IS NOT TRUE
UNION
SELECT pi.paallystyskohde
FROM paallystysilmoitus pi
WHERE pi.paallystyskohde IN (:idt) AND pi.poistettu IS NOT TRUE
UNION
SELECT pai.paikkauskohde
FROM paikkausilmoitus pai
WHERE pai.paikkauskohde IN (:idt) AND pai.poistettu IS NOT TRUE
UNION
SELECT ttm.yllapitokohde
FROM tietyomaa ttm
WHERE ttm.yllapitokohde IN (:idt)
UNION
SELECT ty.yllapitokohde
FROM tarkastus_yllapitokohde ty
  JOIN tarkastus t ON ty.tarkastus = t.id
WHERE ty.yllapitokohde IN (:idt) AND t.poistettu IS NOT TRUE;

-- name: yllapitokohde-sisaltaa-kirjauksia-urakassa
SELECT ((EXISTS(SELECT *
                FROM tiemerkinnan_yksikkohintainen_toteuma
                WHERE yllapitokohde = :yllapitokohde_id AND urakka = :urakka_id)) OR
        (EXISTS(SELECT *
                FROM sanktio s
                WHERE poistettu IS NOT TRUE AND laatupoikkeama IN
                                                (SELECT id
                                                 FROM laatupoikkeama lp
                                                 WHERE yllapitokohde = :yllapitokohde_id
                                                       AND urakka = :urakka_id AND lp.poistettu IS NOT TRUE))) OR
        (EXISTS(SELECT *
                FROM paallystysilmoitus
                WHERE paallystyskohde = :yllapitokohde_id
                      AND (SELECT urakka
                           FROM yllapitokohde
                           WHERE id = :yllapitokohde_id) = :urakka_id)) OR
        (EXISTS(SELECT *
                FROM paikkausilmoitus
                WHERE paikkauskohde = :yllapitokohde_id
                      AND (SELECT urakka
                           FROM yllapitokohde
                           WHERE id = :yllapitokohde_id) = :urakka_id)) OR
        (EXISTS(SELECT *
                FROM laatupoikkeama
                WHERE yllapitokohde = :yllapitokohde_id AND urakka = :urakka_id)) OR
        (EXISTS(SELECT *
                FROM tietyomaa
                WHERE yllapitokohde = :yllapitokohde_id
                      AND (SELECT urakka
                           FROM yllapitokohde
                           WHERE id = :yllapitokohde_id) = :urakka_id)) OR
        (EXISTS(SELECT *
                FROM tarkastus
                WHERE yllapitokohde = :yllapitokohde_id AND urakka = :urakka_id))) AS kirjauksia
FROM yllapitokohde
WHERE id = :yllapitokohde_id;

-- name: hae-urakan-sopimuksen-yllapitokohteet
-- Hakee urakan sopimuksen kaikki yllapitokohteet ja niihin liittyvät ilmoitukset
SELECT
  ypk.id,
  ypk.muokattu,
  pi.id                                 AS "paallystysilmoitus-id",
  pi.tila                               AS "paallystysilmoitus-tila",
  pai.id                                AS "paikkausilmoitus-id",
  pai.tila                              AS "paikkausilmoitus-tila",
  ypk.kohdenumero,
  ypk.nimi,
  ypk.tunnus,
  ypkk.sopimuksen_mukaiset_tyot          AS "sopimuksen-mukaiset-tyot",
  ypkk.arvonvahennykset,
  ypkk.bitumi_indeksi                    AS "bitumi-indeksi",
  ypkk.kaasuindeksi,
  ypkk.toteutunut_hinta                  AS "toteutunut-hinta",
  ypk.nykyinen_paallyste                AS "nykyinen-paallyste",
  ypk.keskimaarainen_vuorokausiliikenne AS "keskimaarainen-vuorokausiliikenne",
  yllapitoluokka,
  ypk.tr_numero                         AS "tr-numero",
  ypk.tr_alkuosa                        AS "tr-alkuosa",
  ypk.tr_alkuetaisyys                   AS "tr-alkuetaisyys",
  ypk.tr_loppuosa                       AS "tr-loppuosa",
  ypk.tr_loppuetaisyys                  AS "tr-loppuetaisyys",
  ypk.tr_ajorata                        AS "tr-ajorata",
  ypk.tr_kaista                         AS "tr-kaista",
  ypk.yhaid,
  ypk.yha_kohdenumero                   AS "yha-kohdenumero",
  ypk.yllapitokohdetyyppi,
  ypk.yllapitokohdetyotyyppi,
  ypk.vuodet,
  ypka.kohde_alku                       AS "kohde-alkupvm",
  ypka.paallystys_alku                  AS "paallystys-alkupvm",
  ypka.paallystys_loppu                 AS "paallystys-loppupvm",
  ypka.tiemerkinta_alku                 AS "tiemerkinta-alkupvm",
  ypka.tiemerkinta_loppu                AS "tiemerkinta-loppupvm",
  ypka.kohde_valmis                     AS "kohde-valmispvm",
  sum(-s.maara)                         AS "sakot-ja-bonukset", -- käännetään toisin päin jotta summaus toimii oikein
  o.nimi                                AS "urakoitsija",
  u.nimi                                AS "urakka",
  u.id                                  AS "urakka-id"
FROM yllapitokohde ypk
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = ypk.id
                                     AND pi.poistettu IS NOT TRUE
  LEFT JOIN paikkausilmoitus pai ON pai.paikkauskohde = ypk.id
                                    AND pai.poistettu IS NOT TRUE
  LEFT JOIN urakka u ON ypk.urakka = u.id
  LEFT JOIN laatupoikkeama lp ON (lp.yllapitokohde = ypk.id AND lp.urakka = ypk.urakka AND lp.poistettu IS NOT TRUE)
  LEFT JOIN sanktio s ON s.laatupoikkeama = lp.id AND s.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
  LEFT JOIN yllapitokohteen_kustannukset ypkk ON ypkk.yllapitokohde = ypk.id
  LEFT JOIN organisaatio o ON (SELECT urakoitsija
                               FROM urakka
                               WHERE id = ypk.urakka) = o.id
WHERE
  ypk.urakka = :urakka
  AND sopimus = :sopimus
  AND (:vuosi :: INTEGER IS NULL OR (cardinality(vuodet) = 0
                                     OR vuodet @> ARRAY [:vuosi] :: INT []))
  AND ypk.poistettu IS NOT TRUE
GROUP BY ypk.id, pi.id, pai.id, o.nimi, u.nimi, u.id,
  ypka.kohde_alku, ypka.paallystys_alku, ypka.paallystys_loppu, ypka.tiemerkinta_alku, ypka.tiemerkinta_loppu,
  ypka.kohde_valmis, ypkk.sopimuksen_mukaiset_tyot, ypkk.arvonvahennykset, ypkk.bitumi_indeksi, ypkk.kaasuindeksi, ypkk.toteutunut_hinta;

-- name: hae-tiemerkintaurakalle-osoitetut-yllapitokohteet
SELECT
  ypk.id,
  ypk.kohdenumero,
  ypk.nimi,
  ypk.tr_numero         AS "tr-numero",
  ypk.tr_alkuosa        AS "tr-alkuosa",
  ypk.tr_alkuetaisyys   AS "tr-alkuetaisyys",
  ypk.tr_loppuosa       AS "tr-loppuosa",
  ypk.tr_loppuetaisyys  AS "tr-loppuetaisyys",
  ypk.tr_ajorata        AS "tr-ajorata",
  ypk.tr_kaista         AS "tr-kaista",
  ypk.yhaid             AS "yha-id",
  ypk.yha_kohdenumero   AS "yha-kohdenumero",
  ypk.yllapitoluokka    AS "yllapitoluokka",
  ypko.id               AS kohdeosa_id,
  ypko.nimi             AS kohdeosa_nimi,
  ypko.tr_numero        AS "kohdeosa_tr-numero",
  ypko.tr_alkuosa       AS "kohdeosa_tr-alkuosa",
  ypko.tr_alkuetaisyys  AS "kohdeosa_tr-alkuetaisyys",
  ypko.tr_loppuosa      AS "kohdeosa_tr-loppuosa",
  ypko.tr_loppuetaisyys AS "kohdeosa_tr-loppuetaisyys",
  ypko.tr_ajorata       AS "kohdeosa_tr-ajorata",
  ypko.tr_kaista        AS "kohdeosa_tr-kaista"
FROM yllapitokohde ypk
  LEFT JOIN yllapitokohdeosa ypko ON ypko.yllapitokohde = ypk.id
                                     AND ypko.poistettu IS NOT TRUE
WHERE
  ypk.suorittava_tiemerkintaurakka = :urakka
  AND (:vuosi :: INTEGER IS NULL OR (cardinality(vuodet) = 0
                                     OR vuodet @> ARRAY [:vuosi] :: INT []))
  AND ypk.poistettu IS NOT TRUE;

-- name: hae-urakan-yllapitokohteet-lomakkeelle
-- Hakee urakan kaikki yllapitokohteet, listaten vain minimaalisen määrän tietoa
SELECT
  ypk.id,
  ypk.kohdenumero,
  ypk.nimi,
  ypk.tr_numero        AS "tr-numero",
  ypk.tr_alkuosa       AS "tr-alkuosa",
  ypk.tr_alkuetaisyys  AS "tr-alkuetaisyys",
  ypk.tr_loppuosa      AS "tr-loppuosa",
  ypk.tr_loppuetaisyys AS "tr-loppuetaisyys",
  ypk.tr_ajorata       AS "tr-ajorata",
  ypk.tr_kaista        AS "tr-kaista"
FROM yllapitokohde ypk
WHERE
  ((urakka = :urakka AND sopimus = :sopimus)
   OR suorittava_tiemerkintaurakka = :urakka)
  AND ypk.poistettu IS NOT TRUE
ORDER BY tr_numero, tr_alkuosa, tr_alkuetaisyys;

-- name: hae-urakan-yllapitokohteen-yllapitokohdeosat
-- Hakee urakan ylläpitokohdeosat ylläpitokohteen id:llä.
SELECT
  ypko.id,
  ypko.nimi,
  ypko.tr_numero        AS "tr-numero",
  ypko.tr_alkuosa       AS "tr-alkuosa",
  ypko.tr_alkuetaisyys  AS "tr-alkuetaisyys",
  ypko.tr_loppuosa      AS "tr-loppuosa",
  ypko.tr_loppuetaisyys AS "tr-loppuetaisyys",
  ypko.tr_ajorata       AS "tr-ajorata",
  ypko.tr_kaista        AS "tr-kaista",
  paallystetyyppi,
  raekoko,
  tyomenetelma,
  massamaara            AS "massamaara",
  toimenpide,
  sijainti
FROM yllapitokohdeosa ypko
  JOIN yllapitokohde ypk ON ypko.yllapitokohde = ypk.id
                            AND ypk.poistettu IS NOT TRUE
WHERE yllapitokohde = :yllapitokohde
      AND ypko.poistettu IS NOT TRUE;

-- name: hae-urakan-yllapitokohteiden-yllapitokohdeosat
-- Hakee urakan ylläpitokohdeosat ylläpitokohteen id:llä.
SELECT
  ypko.id,
  ypk.id                AS "yllapitokohde-id",
  ypko.nimi,
  ypko.tr_numero        AS "tr-numero",
  ypko.tr_alkuosa       AS "tr-alkuosa",
  ypko.tr_alkuetaisyys  AS "tr-alkuetaisyys",
  ypko.tr_loppuosa      AS "tr-loppuosa",
  ypko.tr_loppuetaisyys AS "tr-loppuetaisyys",
  ypko.tr_ajorata       AS "tr-ajorata",
  ypko.tr_kaista        AS "tr-kaista",
  paallystetyyppi,
  raekoko,
  tyomenetelma,
  massamaara            AS "massamaara",
  toimenpide,
  sijainti
FROM yllapitokohdeosa ypko
  JOIN yllapitokohde ypk ON ypko.yllapitokohde = ypk.id
                            AND ypk.poistettu IS NOT TRUE
WHERE yllapitokohde IN (:idt)
      AND ypko.poistettu IS NOT TRUE;

-- name: hae-urakan-yllapitokohteiden-yllapitokohdeosat-alueelle
-- Hakee urakan ylläpitokohdeosat ylläpitokohteen id:llä.
SELECT
  ypko.id,
  ypk.id                             AS "yllapitokohde-id",
  ypko.nimi,
  ypko.tr_numero                     AS "tr-numero",
  ypko.tr_alkuosa                    AS "tr-alkuosa",
  ypko.tr_alkuetaisyys               AS "tr-alkuetaisyys",
  ypko.tr_loppuosa                   AS "tr-loppuosa",
  ypko.tr_loppuetaisyys              AS "tr-loppuetaisyys",
  ypko.tr_ajorata                    AS "tr-ajorata",
  ypko.tr_kaista                     AS "tr-kaista",
  paallystetyyppi,
  raekoko,
  tyomenetelma,
  massamaara                         AS "massamaara",
  toimenpide,
  ST_Simplify(sijainti, :toleranssi) AS sijainti
FROM yllapitokohdeosa ypko
  JOIN yllapitokohde ypk ON ypko.yllapitokohde = ypk.id
                            AND ypk.poistettu IS NOT TRUE
WHERE yllapitokohde IN (:idt)
      AND ypko.poistettu IS NOT TRUE
      AND ST_Intersects(ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax), ypko.sijainti);

-- name: luo-yllapitokohde<!
-- Luo uuden ylläpitokohteen
INSERT INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi,
                           tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
                           tr_ajorata, tr_kaista, keskimaarainen_vuorokausiliikenne,
                           yllapitoluokka, yllapitokohdetyyppi, yllapitokohdetyotyyppi, vuodet)
VALUES (:urakka,
  :sopimus,
  :kohdenumero,
  :nimi,
  :tr_numero,
  :tr_alkuosa,
  :tr_alkuetaisyys,
  :tr_loppuosa,
  :tr_loppuetaisyys,
  :tr_ajorata,
  :tr_kaista,
  :keskimaarainen_vuorokausiliikenne,
  :yllapitoluokka,
  :yllapitokohdetyyppi :: YLLAPITOKOHDETYYPPI,
  :yllapitokohdetyotyyppi :: YLLAPITOKOHDETYOTYYPPI,
  :vuodet :: INTEGER []);

-- name: paivita-yllapitokohde<!
-- Päivittää ylläpitokohteen
UPDATE yllapitokohde
SET
  kohdenumero                       = :kohdenumero,
  nimi                              = :nimi,
  tunnus                            = :tunnus,
  tr_numero                         = :tr_numero,
  tr_alkuosa                        = :tr_alkuosa,
  tr_alkuetaisyys                   = :tr_alkuetaisyys,
  tr_loppuosa                       = :tr_loppuosa,
  tr_loppuetaisyys                  = :tr_loppuetaisyys,
  tr_ajorata                        = :tr_ajorata,
  tr_kaista                         = :tr_kaista,
  keskimaarainen_vuorokausiliikenne = :keskimaarainen_vuorokausiliikenne,
  yllapitoluokka                    = :yllapitoluokka,
  muokattu                          = now()
WHERE id = :id
      AND urakka = :urakka;

-- name: poista-yllapitokohde!
-- Poistaa ylläpitokohteen
UPDATE yllapitokohde
SET poistettu = TRUE,
  muokattu    = NOW()
WHERE id = :id
      AND urakka = :urakka;

-- name: luo-yllapitokohdeosa<!
-- Luo uuden yllapitokohdeosan
INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys,
                              tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, toimenpide,
                              paallystetyyppi, raekoko, tyomenetelma, massamaara,
                              ulkoinen_id, sijainti)
VALUES (:yllapitokohde,
  :nimi,
  :tr_numero,
  :tr_alkuosa,
  :tr_alkuetaisyys,
  :tr_loppuosa,
  :tr_loppuetaisyys,
  :tr_ajorata,
  :tr_kaista,
  :toimenpide,
  :paallystetyyppi,
  :raekoko,
  :tyomenetelma,
  :massamaara,
  :ulkoinen-id,
  (SELECT tierekisteriosoitteelle_viiva_ajr AS geom
   FROM tierekisteriosoitteelle_viiva_ajr(CAST(:tr_numero AS INTEGER),
                                          CAST(:tr_alkuosa AS INTEGER),
                                          CAST(:tr_alkuetaisyys AS INTEGER),
                                          CAST(:tr_loppuosa AS INTEGER),
                                          CAST(:tr_loppuetaisyys AS INTEGER),
                                          CAST(:tr_ajorata AS INTEGER))));

-- name: luo-yllapitokohdeosa-paallystysilmoituksen-apista<!
-- Luo uuden yllapitokohdeosan
INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys,
                              tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, ulkoinen_id, sijainti)
VALUES (:yllapitokohde,
        :nimi,
        :tr_numero,
        :tr_alkuosa,
        :tr_alkuetaisyys,
        :tr_loppuosa,
        :tr_loppuetaisyys,
        :tr_ajorata,
        :tr_kaista,
        :ulkoinen-id,
        (SELECT tierekisteriosoitteelle_viiva AS geom
         FROM tierekisteriosoitteelle_viiva(CAST(:tr_numero AS INTEGER),
                                            CAST(:tr_alkuosa AS INTEGER),
                                            CAST(:tr_alkuetaisyys AS INTEGER),
                                            CAST(:tr_loppuosa AS INTEGER),
                                            CAST(:tr_loppuetaisyys AS INTEGER))));

-- name: paivita-yllapitokohdeosa<!
-- Päivittää yllapitokohdeosan
UPDATE yllapitokohdeosa
SET
  nimi             = :nimi,
  tr_numero        = :tr_numero,
  tr_alkuosa       = :tr_alkuosa,
  tr_alkuetaisyys  = :tr_alkuetaisyys,
  tr_loppuosa      = :tr_loppuosa,
  tr_loppuetaisyys = :tr_loppuetaisyys,
  tr_ajorata       = :tr_ajorata,
  tr_kaista        = :tr_kaista,
  paallystetyyppi  = :paallystetyyppi,
  raekoko          = :raekoko,
  tyomenetelma     = :tyomenetelma,
  massamaara       = :massamaara,
  toimenpide       = :toimenpide,
  muokattu         = NOW(),
  sijainti         = (SELECT tierekisteriosoitteelle_viiva_ajr AS geom
                      FROM tierekisteriosoitteelle_viiva_ajr(CAST(:tr_numero AS INTEGER),
                                                             CAST(:tr_alkuosa AS INTEGER),
                                                             CAST(:tr_alkuetaisyys AS INTEGER),
                                                             CAST(:tr_loppuosa AS INTEGER),
                                                             CAST(:tr_loppuetaisyys AS INTEGER),
                                                             CAST(:tr_ajorata AS INTEGER)))
WHERE id = :id
      AND yllapitokohde IN (SELECT id
                            FROM yllapitokohde
                            WHERE urakka = :urakka);

-- name: poista-yllapitokohdeosa!
-- Poistaa ylläpitokohdeosan
UPDATE yllapitokohdeosa
SET poistettu = TRUE,
  muokattu    = NOW()
WHERE id = :id
      AND yllapitokohde IN (SELECT id
                            FROM yllapitokohde
                            WHERE urakka = :urakka);

-- name: merkitse-yllapitokohteen-kohdeosat-poistetuiksi!
UPDATE yllapitokohdeosa
SET poistettu = TRUE,
  muokattu    = NOW()
WHERE yllapitokohde IN (SELECT id
                        FROM yllapitokohde
                        WHERE urakka = :urakka AND
                              id = :yllapitokohdeid);

-- name: hae-paallystysurakan-aikataulu
-- Hakee päällystysurakan kohteiden aikataulutiedot
SELECT
  ypk.id,
  ypk.muokattu,
  ypk.kohdenumero,
  ypk.nimi,
  ypk.urakka,
  ypk.sopimus,
  ypka.kohde_alku                  AS "aikataulu-kohde-alku",
  ypka.paallystys_alku             AS "aikataulu-paallystys-alku",
  ypka.paallystys_loppu            AS "aikataulu-paallystys-loppu",
  ypka.tiemerkinta_takaraja        AS "aikataulu-tiemerkinta-takaraja",
  ypka.tiemerkinta_alku            AS "aikataulu-tiemerkinta-alku",
  ypka.tiemerkinta_loppu           AS "aikataulu-tiemerkinta-loppu",
  ypka.kohde_valmis                AS "aikataulu-kohde-valmis",
  ypka.muokattu                    AS "aikataulu-muokattu",
  ypka.muokkaaja                   AS "aikataulu-muokkaaja",
  ypka.valmis_tiemerkintaan        AS "valmis-tiemerkintaan",
  ypkya.id                         AS "tarkkaaikataulu_id",
  ypkya.toimenpide                 AS "tarkkaaikataulu_toimenpide",
  ypkya.kuvaus                     AS "tarkkaaikataulu_kuvaus",
  ypkya.alku                       AS "tarkkaaikataulu_alku",
  ypkya.loppu                      AS "tarkkaaikataulu_loppu",
  ypkya.urakka                     AS "tarkkaaikataulu_urakka-id",
  ypk.tr_numero                    AS "tr-numero",
  ypk.tr_alkuosa                   AS "tr-alkuosa",
  ypk.tr_alkuetaisyys              AS "tr-alkuetaisyys",
  ypk.tr_loppuosa                  AS "tr-loppuosa",
  ypk.tr_loppuetaisyys             AS "tr-loppuetaisyys",
  ypk.tr_ajorata                   AS "tr-ajorata",
  ypk.tr_kaista                    AS "tr-kaista",
  ypk.yllapitoluokka,
  ypk.suorittava_tiemerkintaurakka AS "suorittava-tiemerkintaurakka",
  tti.id                           AS "tietyoilmoitus-id"
FROM yllapitokohde ypk
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
  LEFT JOIN yllapitokohteen_tarkka_aikataulu ypkya ON ypk.id = ypkya.yllapitokohde
                                                                 AND ypkya.poistettu IS NOT TRUE
  LEFT JOIN tietyoilmoitus tti ON (ypk.id = tti.yllapitokohde AND tti."urakka-id" = :urakka)
WHERE
  ypk.urakka = :urakka
  AND ypk.sopimus = :sopimus
  AND (:vuosi :: INTEGER IS NULL OR (cardinality(vuodet) = 0
                                     OR ypk.vuodet @> ARRAY [:vuosi] :: INT []))
  AND ypk.poistettu IS NOT TRUE
ORDER BY ypka.kohde_alku;

-- name: hae-tiemerkintaurakan-aikataulu
-- Hakee tiemerkintäurakan kohteiden aikataulutiedot
SELECT
  ypk.id,
  ypk.muokattu,
  ypk.kohdenumero,
  ypk.nimi,
  ypk.urakka,
  ypk.sopimus,
  ypka.kohde_alku           AS "aikataulu-kohde-alku",
  ypka.paallystys_alku      AS "aikataulu-paallystys-alku",
  ypka.paallystys_loppu     AS "aikataulu-paallystys-loppu",
  ypka.tiemerkinta_takaraja AS "aikataulu-tiemerkinta-takaraja",
  ypka.tiemerkinta_alku     AS "aikataulu-tiemerkinta-alku",
  ypka.tiemerkinta_loppu    AS "aikataulu-tiemerkinta-loppu",
  ypka.kohde_valmis         AS "aikataulu-kohde-valmis",
  ypka.muokattu             AS "aikataulu-muokattu",
  ypka.muokkaaja            AS "aikataulu-muokkaaja",
  ypka.valmis_tiemerkintaan AS "valmis-tiemerkintaan",
  ypkya.id                  AS "tarkkaaikataulu_id",
  ypkya.toimenpide          AS "tarkkaaikataulu_toimenpide",
  ypkya.kuvaus              AS "tarkkaaikataulu_kuvaus",
  ypkya.alku                AS "tarkkaaikataulu_alku",
  ypkya.loppu               AS "tarkkaaikataulu_loppu",
  ypkya.urakka              AS "tarkkaaikataulu_urakka-id",
  ypk.tr_numero             AS "tr-numero",
  ypk.tr_alkuosa            AS "tr-alkuosa",
  ypk.tr_alkuetaisyys       AS "tr-alkuetaisyys",
  ypk.tr_loppuosa           AS "tr-loppuosa",
  ypk.tr_loppuetaisyys      AS "tr-loppuetaisyys",
  ypk.tr_ajorata            AS "tr-ajorata",
  ypk.tr_kaista             AS "tr-kaista",
  ypk.yllapitoluokka,
  tti.id                    AS "tietyoilmoitus-id",
  paallystysurakka.nimi     AS paallystysurakka,
  sposti.vastaanottajat         AS "sahkopostitiedot_muut-vastaanottajat",
  sposti.saate                  AS sahkopostitiedot_saate,
  sposti.kopio_lahettajalle     AS "sahkopostitiedot_kopio-lahettajalle?"
FROM yllapitokohde ypk
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
  LEFT JOIN yllapitokohteen_tarkka_aikataulu ypkya ON ypk.id = ypkya.yllapitokohde
                                                      AND ypkya.poistettu IS NOT TRUE
  LEFT JOIN tietyoilmoitus tti ON (ypk.id = tti.yllapitokohde AND tti."urakka-id" = :suorittava_tiemerkintaurakka)
  LEFT JOIN urakka paallystysurakka ON ypk.urakka = paallystysurakka.id
  LEFT JOIN yllapitokohteen_sahkopostitiedot sposti ON sposti.yllapitokohde_id = ypk.id

WHERE
  ypk.suorittava_tiemerkintaurakka = :suorittava_tiemerkintaurakka
  AND (:vuosi :: INTEGER IS NULL OR (cardinality(ypk.vuodet) = 0
                                     OR ypk.vuodet @> ARRAY [:vuosi] :: INT []))
  AND ypk.poistettu IS NOT TRUE
ORDER BY ypka.paallystys_alku;

-- name: hae-yllapitokohteen-aikataulu
-- Hakee päällystysurakan kohteiden aikataulutiedot
SELECT
  ypk.id,
  ypka.kohde_alku           AS "kohde-alku",
  ypka.paallystys_alku      AS "paallystys-alku",
  ypka.paallystys_loppu     AS "paallystys-loppu",
  ypka.tiemerkinta_takaraja AS "tiemerkinta-takaraja",
  ypka.tiemerkinta_alku     AS "tiemerkinta-alku",
  ypka.tiemerkinta_loppu    AS "tiemerkinta-loppu",
  ypka.kohde_valmis         AS "kohde-valmis",
  ypka.muokattu             AS "muokattu",
  ypka.muokkaaja            AS "muokkaaja",
  ypka.valmis_tiemerkintaan AS "valmis-tiemerkintaan"
FROM yllapitokohde ypk
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
WHERE ypk.id = :id
      AND poistettu IS NOT TRUE;

-- name: hae-yllapitokohteiden-aikataulun-muokkaus-aika
-- Hakee yllapitokohteiden aikataulutiedot
SELECT
  muokattu,
  yllapitokohde
FROM yllapitokohteen_aikataulu
WHERE yllapitokohde IN (:idt);

-- name: hae-urakan-tyyppi
SELECT tyyppi
FROM urakka
WHERE id = :urakka;

-- name: hae-tiemerkinnan-suorittavat-urakat
SELECT
  id,
  nimi,
  hallintayksikko
FROM urakka
WHERE (loppupvm IS NULL OR loppupvm >= current_date)
      AND tyyppi = 'tiemerkinta' :: URAKKATYYPPI;

-- name: tallenna-paallystyskohteen-aikataulu!
-- Tallentaa ylläpitokohteen aikataulun
UPDATE yllapitokohteen_aikataulu
SET
  kohde_alku       = :aikataulu_kohde_alku,
  paallystys_alku  = :aikataulu_paallystys_alku,
  paallystys_loppu = :aikataulu_paallystys_loppu,
  kohde_valmis     = :aikataulu_kohde_valmis,
  muokattu         = NOW(),
  muokkaaja        = :aikataulu_muokkaaja
WHERE yllapitokohde = :id
      AND (SELECT urakka
           FROM yllapitokohde
           WHERE id = :id) = :urakka;

-- name: tallenna-yllapitokohteen-kustannukset!
-- Tallentaa ylläpitokohteen kustannukset
UPDATE yllapitokohteen_kustannukset
SET
  sopimuksen_mukaiset_tyot = :sopimuksen_mukaiset_tyot,
  arvonvahennykset         = :arvonvahennykset,
  bitumi_indeksi           = :bitumi_indeksi,
  kaasuindeksi             = :kaasuindeksi,
  toteutunut_hinta         = :toteutunut_hinta,
  muokattu                 = NOW(),
  muokkaaja                = :muokkaaja
WHERE yllapitokohde = :yllapitokohde
      AND (SELECT urakka
           FROM yllapitokohde
           WHERE id = :yllapitokohde) = :urakka;

-- name: tallenna-yllapitokohteen-suorittava-tiemerkintaurakka!
-- Tallentaa ylläpitokohteen aikataulun
UPDATE yllapitokohde
SET
  suorittava_tiemerkintaurakka = :suorittava_tiemerkintaurakka,
  muokattu = NOW()
WHERE id = :id
      AND urakka = :urakka;

-- name: paivita-yllapitokohteen-numero-ja-nimi!
UPDATE yllapitokohde
SET
  kohdenumero = :kohdenumero,
  nimi        = :nimi,
  muokattu = NOW()
WHERE id = :id
      AND urakka = :urakka;

-- name: tallenna-yllapitokohteen-valmis-viimeistaan-paallystysurakasta!
-- Tallentaa ylläpitokohteen valmis viimeistään -sarakkeen tiedon
UPDATE yllapitokohteen_aikataulu
SET
  tiemerkinta_takaraja = :aikataulu_tiemerkinta_takaraja
WHERE yllapitokohde = :id
      AND (SELECT urakka
           FROM yllapitokohde
           WHERE id = :id) = :urakka;

-- name: tallenna-yllapitokohteen-valmis-viimeistaan-tiemerkintaurakasta!
-- Tallentaa ylläpitokohteen valmis viimeistään -sarakkeen tiedon
UPDATE yllapitokohteen_aikataulu
SET
  tiemerkinta_takaraja = :aikataulu_tiemerkinta_takaraja
WHERE yllapitokohde = :id
      AND (SELECT suorittava_tiemerkintaurakka
           FROM yllapitokohde
           WHERE id = :id) = :suorittava_tiemerkintaurakka;

-- name: merkitse-kohde-valmiiksi-tiemerkintaan<!
UPDATE yllapitokohteen_aikataulu
SET
  valmis_tiemerkintaan = :valmis_tiemerkintaan,
  tiemerkinta_takaraja = :aikataulu_tiemerkinta_takaraja
WHERE yllapitokohde = :id
      AND (SELECT urakka
           FROM yllapitokohde
           WHERE id = :id) = :urakka;

-- name: hae-yllapitokohteiden-tiedot-sahkopostilahetykseen
SELECT
  ypk.id                 AS id,
  ypk.nimi               AS "kohde-nimi",
  ypk.tr_numero          AS "tr-numero",
  ypk.tr_alkuosa         AS "tr-alkuosa",
  ypk.tr_alkuetaisyys       AS "tr-alkuetaisyys",
  ypk.tr_loppuosa           AS "tr-loppuosa",
  ypk.tr_loppuetaisyys      AS "tr-loppuetaisyys",
  ypka.tiemerkinta_loppu    AS "aikataulu-tiemerkinta-loppu",
  pu.id                     AS "paallystysurakka-id",
  pu.nimi                   AS "paallystysurakka-nimi",
  pu.sampoid                AS "paallystysurakka-sampo-id",
  tu.id                     AS "tiemerkintaurakka-id",
  tu.nimi                   AS "tiemerkintaurakka-nimi",
  tu.sampoid                AS "tiemerkintaurakka-sampo-id",
  sposti.vastaanottajat     AS "sahkopostitiedot_muut-vastaanottajat",
  sposti.saate              AS sahkopostitiedot_saate,
  sposti.kopio_lahettajalle AS "sahkopostitiedot_kopio-lahettajalle?"
FROM yllapitokohde ypk
  JOIN urakka pu ON ypk.urakka = pu.id
  LEFT JOIN urakka tu ON ypk.suorittava_tiemerkintaurakka = tu.id
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
  LEFT JOIN yllapitokohteen_sahkopostitiedot sposti ON sposti.yllapitokohde_id = ypk.id
WHERE ypk.id IN (:idt);

-- name: hae-tanaan-valmistuvien-tiemerkintakohteiden-idt
SELECT
  ypk.id                 AS id
FROM yllapitokohde ypk
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
WHERE
  ypka.tiemerkinta_loppu :: DATE = now() :: DATE;

-- name: tallenna-valmistuneen-tiemerkkinnan-sahkopostitiedot<!
INSERT INTO yllapitokohteen_sahkopostitiedot (tyyppi, yllapitokohde_id, vastaanottajat, saate, kopio_lahettajalle)
    VALUES ('tiemerkinta_valmistunut'::yllapitokohteen_sahkopostitiedot_tyyppi, :yllapitokohde_id,
            :vastaanottajat::TEXT[], :saate, :kopio_lahettajalle);

-- name: poista-valmistuneen-tiemerkinnan-sahkopostitiedot!
DELETE FROM yllapitokohteen_sahkopostitiedot WHERE yllapitokohde_id IN (:yllapitokohde_id) AND tyyppi = 'tiemerkinta_valmistunut'::yllapitokohteen_sahkopostitiedot_tyyppi;

-- name: tallenna-tiemerkintakohteen-aikataulu!
-- Tallentaa ylläpitokohteen aikataulun
UPDATE yllapitokohteen_aikataulu
SET
  tiemerkinta_alku  = :aikataulu_tiemerkinta_alku,
  tiemerkinta_loppu = :aikataulu_tiemerkinta_loppu,
  muokattu          = NOW(),
  muokkaaja         = :aikataulu_muokkaaja
WHERE yllapitokohde = :id
      AND (SELECT suorittava_tiemerkintaurakka
           FROM yllapitokohde
           WHERE id = :id) = :suorittava_tiemerkintaurakka;

-- name: paivita-yllapitokohteen-tarkka-aikataulu!
UPDATE yllapitokohteen_tarkka_aikataulu
SET
  toimenpide = :toimenpide :: YLLAPITOKOHTEEN_AIKATAULU_TOIMENPIDE,
  kuvaus     = :kuvaus,
  alku       = :alku,
  loppu      = :loppu,
  muokkaaja  = :muokkaaja,
  muokattu   = NOW(),
  poistettu  = :poistettu
WHERE id = :id
      AND yllapitokohde = :yllapitokohde
      AND urakka = :urakka;

-- name: lisaa-yllapitokohteen-tarkka-aikataulu!
-- Tallentaa ylläpitokohteen yksityiskohtaisen aikataulun
INSERT INTO yllapitokohteen_tarkka_aikataulu (yllapitokohde, urakka, toimenpide, kuvaus, alku, loppu, luoja, luotu)
VALUES
  (:yllapitokohde, :urakka, :toimenpide :: YLLAPITOKOHTEEN_AIKATAULU_TOIMENPIDE, :kuvaus, :alku, :loppu, :luoja, NOW());

-- name: hae-yllapitokohteen-urakka-id
SELECT urakka AS id
FROM yllapitokohde
WHERE id = :id;

-- name: hae-yllapitokohteen-suorittava-tiemerkintaurakka-id
SELECT suorittava_tiemerkintaurakka AS id
FROM yllapitokohde
WHERE id = :id;

-- name: hae-yllapitokohde
SELECT
  ypk.id,
  ypk.urakka,
  sopimus,
  kohdenumero,
  nimi,
  ypkk.sopimuksen_mukaiset_tyot,
  ypkk.arvonvahennykset,
  ypkk.bitumi_indeksi,
  ypkk.kaasuindeksi,
  poistettu,
  ypka.kohde_alku            AS "aikataulu_kohde_alku",
  ypka.paallystys_alku       AS "aikataulu_paallystys_alku",
  ypka.paallystys_loppu      AS "aikataulu_paallystys_loppu",
  ypka.tiemerkinta_alku      AS "aikataulu_tiemerkinta_alku",
  ypka.tiemerkinta_loppu     AS "aikataulu_tiemerkinta_loppu",
  ypka.kohde_valmis          AS "aikataulu_kohde_valmis",
  ypka.muokattu              AS "aikataulu_muokattu",
  ypka.muokkaaja             AS "aikataulu_muokkaaja",
  ypka.valmis_tiemerkintaan,
  tr_numero                  AS "tr-numero",
  tr_alkuosa                 AS "tr-alkuosa",
  tr_alkuetaisyys            AS "tr-alkuetaisyys",
  tr_loppuosa                AS "tr-loppuosa",
  tr_loppuetaisyys           AS "tr-loppuetaisyys",
  tr_ajorata                 AS "tr-ajorata",
  tr_kaista                  AS "tr-kaista",
  yllapitokohdetyotyyppi,
  yllapitokohdetyyppi,
  tunnus,
  yhaid,
  yha_kohdenumero            AS "yha-kohdenumero",
  yllapitoluokka,
  lahetysaika,
  keskimaarainen_vuorokausiliikenne,
  nykyinen_paallyste,
  suorittava_tiemerkintaurakka,
  (SELECT viimeisin_paivitys
   FROM geometriapaivitys
   WHERE nimi = 'tieverkko') AS karttapvm
FROM yllapitokohde ypk
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
  LEFT JOIN yllapitokohteen_kustannukset ypkk ON ypkk.yllapitokohde = ypk.id
WHERE ypk.id = :id;

-- name: hae-yllapitokohteen-tiemerkintaaikataulu
SELECT
  tiemerkinta_alku     AS "tiemerkinta-alku",
  tiemerkinta_loppu    AS "tiemerkinta-loppu",
  tiemerkinta_takaraja AS "tiemerkinta-takaraja"
FROM yllapitokohteen_aikataulu
WHERE yllapitokohde = :id;

-- name: merkitse-kohteen-lahetystiedot!
UPDATE yllapitokohde
SET lahetetty = :lahetetty, lahetys_onnistunut = :onnistunut, lahetysvirhe = :lahetysvirhe
WHERE id = :kohdeid;

-- name: onko-olemassa-urakalla?
-- single?: true
SELECT exists(SELECT id
              FROM yllapitokohde
              WHERE urakka = :urakka AND id = :kohde);

-- name: paivita-yllapitokohteen-sijainti!
-- Päivittää ylläpitokohteen sijainnin
UPDATE yllapitokohde
SET
  tr_alkuosa       = :tr_alkuosa,
  tr_alkuetaisyys  = :tr_alkuetaisyys,
  tr_loppuosa      = :tr_loppuosa,
  tr_loppuetaisyys = :tr_loppuetaisyys,
  muokattu = NOW()
WHERE id = :id;

-- name: paivita-yllapitokohteen-paallystysaikataulu!
-- Päivittää ylläpitokohteen aikataulutiedot
UPDATE yllapitokohteen_aikataulu
SET
  kohde_alku           = :kohde_alku,
  paallystys_alku      = :paallystys_alku,
  paallystys_loppu     = :paallystys_loppu,
  kohde_valmis         = :kohde_valmis,
  valmis_tiemerkintaan = :valmis_tiemerkintaan,
  muokattu             = NOW(),
  muokkaaja            = :muokkaaja
WHERE yllapitokohde = :id;

-- name: paivita-yllapitokohteen-paallystysilmoituksen-aikataulu<!
-- Päivittää päällystysilmoituksen aikataulutiedot
UPDATE paallystysilmoitus
SET
  takuupvm = :takuupvm
WHERE paallystyskohde = :kohde_id
      AND poistettu IS NOT TRUE;

-- name: paivita-yllapitokohteen-tiemerkintaaikataulu!
-- Päivittää ylläpitokohteen aikataulutiedot
UPDATE yllapitokohteen_aikataulu
SET
  tiemerkinta_alku  = :tiemerkinta_alku,
  tiemerkinta_loppu = :tiemerkinta_loppu,
  muokattu          = NOW(),
  muokkaaja         = :muokkaaja
WHERE yllapitokohde = :id;

-- name: poista-yllapitokohteen-kohdeosat!
DELETE FROM yllapitokohdeosa
WHERE yllapitokohde = :id;

-- name: hae-kohteen-tienumero
SELECT tr_numero
FROM yllapitokohde
WHERE id = :kohdeid;

-- name: paivita-paallystys-tai-paikkausurakan-geometria
SELECT paivita_paallystys_tai_paikkausurakan_geometria(:urakka :: INTEGER);

-- name: luo-yllapitokohteelle-tyhja-aikataulu<!
INSERT INTO yllapitokohteen_aikataulu (yllapitokohde) VALUES (:yllapitokohde);

-- name: luo-yllapitokohteelle-kustannukset<!
INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, toteutunut_hinta, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES (:yllapitokohde, :toteutunut_hinta, :sopimuksen_mukaiset_tyot, :arvonvahennykset, :bitumi_indeksi, :kaasuindeksi);

-- name: luo-yllapitokohteelle-tyhja-kustannustaulu<!
INSERT INTO yllapitokohteen_kustannukset (yllapitokohde, toteutunut_hinta, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES (:yllapitokohde, 0, 0, 0, 0, 0);

-- name: hae-yhden-vuoden-yha-kohteet
SELECT
  ypk.id,
  u.nimi           AS "urakka",
  u.id             AS "urakka-id",
  ypk.nimi,
  kohdenumero,
  tr_numero        AS "tr-numero",
  tr_alkuosa       AS "tr-alkuosa",
  tr_alkuetaisyys  AS "tr-alkuetaisyys",
  tr_loppuosa      AS "tr-loppuosa",
  tr_loppuetaisyys AS "tr-loppuetaisyys",
  tr_ajorata       AS "tr-ajorata",
  tr_kaista        AS "tr-kaista"
FROM yllapitokohde ypk
  LEFT JOIN urakka u ON ypk.urakka = u.id
WHERE vuodet @> ARRAY [:vuosi] :: INT []
      AND ypk.poistettu IS NOT TRUE
      AND yhaid IS NOT NULL;

-- name: hae-yhden-vuoden-muut-kohdeosat
SELECT
  ypko.id,
  ypk.id           AS "kohde-id",
  u.nimi           AS "urakka",
  u.id             AS "urakka-id",
  ypk.nimi         AS "kohteen-nimi",
  ypko.nimi,
  ypk.kohdenumero,
  ypko.tr_numero        AS "tr-numero",
  ypko.tr_alkuosa       AS "tr-alkuosa",
  ypko.tr_alkuetaisyys  AS "tr-alkuetaisyys",
  ypko.tr_loppuosa      AS "tr-loppuosa",
  ypko.tr_loppuetaisyys AS "tr-loppuetaisyys",
  ypko.tr_ajorata       AS "tr-ajorata",
  ypko.tr_kaista        AS "tr-kaista"
FROM yllapitokohdeosa ypko
  LEFT JOIN yllapitokohde ypk ON ypko.yllapitokohde = ypk.id
  LEFT JOIN urakka u ON ypk.urakka = u.id
WHERE vuodet @> ARRAY [:vuosi] :: INT [] AND
      ypko.poistettu IS NOT TRUE AND
      ypko.tr_numero = :tr-numero AND
      ypko.yhaid IS NOT NULL;

-- name: hae-yhden-vuoden-kohdeosat-teille
SELECT
  u.nimi                AS "urakan-nimi",
  ypk.nimi              AS "paakohteen-nimi",
  ypk.tunnus            AS "paakohteen-tunnus",
  ypk.kohdenumero       AS "paakohteen-kohdenumero",
  ypko.nimi             AS "yllapitokohteen-nimi",
  ypko.id,
  ypko.tr_numero        AS "tr-numero",
  ypko.tr_alkuosa       AS "tr-alkuosa",
  ypko.tr_alkuetaisyys  AS "tr-alkuetaisyys",
  ypko.tr_loppuosa      AS "tr-loppuosa",
  ypko.tr_loppuetaisyys AS "tr-loppuetaisyys",
  ypko.tr_ajorata       AS "tr-ajorata",
  ypko.tr_kaista        AS "tr-kaista"
FROM yllapitokohdeosa ypko
  LEFT JOIN yllapitokohde ypk ON ypko.yllapitokohde = ypk.id AND NOT ypk.id = :yllapitokohdeid
  LEFT JOIN urakka u ON ypk.urakka = u.id
WHERE vuodet @> ARRAY [:vuosi] :: INT [] AND
      ypko.poistettu IS NOT TRUE AND
      ypko.tr_numero IN (:tiet);

-- name: hae-yllapitokohteen-vuodet
-- Hakee urakan ylläpitokohteen vuodet, joilla kohdetta työstetään.
SELECT vuodet FROM yllapitokohde
WHERE id = :id;
