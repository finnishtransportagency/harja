-- name: hae-kaikki-urakan-yllapitokohteet
-- Hakee urakan kaikki yllapitokohteet, myös tiemerkinnän kautta suoritettavat,
-- sekä niiden päällystysilmoitukset ja alikohteet
SELECT
  ypk.id,
  ypk.urakka,
  ypk.sopimus,
  ypk.kohdenumero,
  ypk.nimi,
  ypk.yllapitokohdetyyppi,
  ypk.yllapitokohdetyotyyppi,
  ypk.sopimuksen_mukaiset_tyot          AS "sopimuksen-mukaiset-tyot",
  ypk.arvonvahennykset,
  ypk.bitumi_indeksi                    AS "bitumi-indeksi",
  ypk.kaasuindeksi,
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
  ypko.tunnus                           AS "kohdeosa_tunnus",
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
  pi.takuupvm                           AS "paallystysilmoitus_takuupvm"
FROM yllapitokohde ypk
  LEFT JOIN yllapitokohdeosa ypko ON ypk.id = ypko.yllapitokohde AND ypko.poistettu IS NOT TRUE
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = ypk.id AND pi.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
WHERE
  (ypk.urakka = :urakka OR ypk.suorittava_tiemerkintaurakka = :urakka)
  AND ypk.poistettu IS NOT TRUE;

-- name: hae-urakkaan-liittyvat-paallystyskohteet
-- Hakee ylläpitokohteet, jotka ovat kyseisen urakan päällystyskohteita
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
SELECT NOT (((SELECT COUNT(yhaid)
              FROM yllapitokohde
              WHERE id = :id AND yhaid IS NOT NULL) > 0) OR
            ((SELECT COUNT(*)
              FROM tiemerkinnan_yksikkohintainen_toteuma tyt
              WHERE yllapitokohde = :id AND tyt.poistettu IS NOT TRUE) > 0) OR
            ((SELECT COUNT(*)
              FROM sanktio s
              WHERE poistettu IS NOT TRUE AND laatupoikkeama IN
                                              (SELECT id
                                               FROM laatupoikkeama lp
                                               WHERE yllapitokohde = :id AND lp.poistettu IS NOT TRUE)) > 0) OR
            ((SELECT COUNT(*)
              FROM paallystysilmoitus pi
              WHERE paallystyskohde = :id AND pi.poistettu IS NOT TRUE) > 0) OR
            ((SELECT COUNT(*)
              FROM paikkausilmoitus pai
              WHERE paikkauskohde = :id AND pai.poistettu IS NOT TRUE) > 0) OR
            ((SELECT COUNT(*)
              FROM tietyomaa ttm
              WHERE yllapitokohde = :id) > 0) OR
            ((SELECT COUNT(*)
              FROM laatupoikkeama lp
              WHERE yllapitokohde = :id AND lp.poistettu IS NOT TRUE) > 0) OR
            ((SELECT COUNT(*)
              FROM tarkastus t
              WHERE yllapitokohde = :id AND t.poistettu IS NOT TRUE) > 0)) AS "saa-poistaa"
FROM yllapitokohde
WHERE id = :id;

-- name: yllapitokohde-sisaltaa-kirjauksia-urakassa
SELECT (((SELECT COUNT(*)
          FROM tiemerkinnan_yksikkohintainen_toteuma
          WHERE yllapitokohde = :yllapitokohde_id AND urakka = :urakka_id) > 0) OR
        -- Seuraavat asiat otetaan mukaan jos ylläpitokohteen urakka on annettu urakka
        ((SELECT COUNT(*)
          FROM sanktio s
          WHERE poistettu IS NOT TRUE AND laatupoikkeama IN
                                          (SELECT id
                                           FROM laatupoikkeama lp
                                           WHERE yllapitokohde = :yllapitokohde_id
                                                 AND urakka = :urakka_id AND lp.poistettu IS NOT TRUE)) > 0) OR
        ((SELECT COUNT(*)
          FROM paallystysilmoitus
          WHERE paallystyskohde = :yllapitokohde_id
                AND (SELECT urakka
                     FROM yllapitokohde
                     WHERE id = :yllapitokohde_id) = :urakka_id) > 0) OR
        ((SELECT COUNT(*)
          FROM paikkausilmoitus
          WHERE paikkauskohde = :yllapitokohde_id
                AND (SELECT urakka
                     FROM yllapitokohde
                     WHERE id = :yllapitokohde_id) = :urakka_id) > 0) OR
        ((SELECT COUNT(*)
          FROM laatupoikkeama
          WHERE yllapitokohde = :yllapitokohde_id AND urakka = :urakka_id) > 0) OR
        ((SELECT COUNT(*)
          FROM tietyomaa
          WHERE yllapitokohde = :yllapitokohde_id
                AND (SELECT urakka
                     FROM yllapitokohde
                     WHERE id = :yllapitokohde_id) = :urakka_id) > 0) OR
        ((SELECT COUNT(*)
          FROM tarkastus
          WHERE yllapitokohde = :yllapitokohde_id AND urakka = :urakka_id) > 0)) AS kirjauksia
FROM yllapitokohde
WHERE id = :yllapitokohde_id;

-- name: hae-urakan-sopimuksen-yllapitokohteet
-- Hakee urakan sopimuksen kaikki yllapitokohteet ja niihin liittyvät ilmoitukset
SELECT
  ypk.id,
  pi.id                                 AS "paallystysilmoitus-id",
  pi.tila                               AS "paallystysilmoitus-tila",
  pai.id                                AS "paikkausilmoitus-id",
  pai.tila                              AS "paikkausilmoitus-tila",
  pai.toteutunut_hinta                  AS "toteutunut-hinta",
  ypk.kohdenumero,
  ypk.nimi,
  ypk.sopimuksen_mukaiset_tyot          AS "sopimuksen-mukaiset-tyot",
  ypk.arvonvahennykset,
  ypk.bitumi_indeksi                    AS "bitumi-indeksi",
  ypk.kaasuindeksi,
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
  ypka.kohde_alku                       AS "kohde-alkupvm",
  ypka.paallystys_alku                  AS "paallystys-alkupvm",
  ypka.paallystys_loppu                 AS "paallystys-loppupvm",
  ypka.tiemerkinta_alku                 AS "tiemerkinta-alkupvm",
  ypka.tiemerkinta_loppu                AS "tiemerkinta-loppupvm",
  ypka.kohde_valmis                     AS "kohde-valmispvm",
  sum(s.maara)                          AS "bonukset-ja-sakot",
  o.nimi                                AS "urakoitsija",
  u.nimi                                AS "urakka",
  u.id                                  AS "urakka-id"
FROM yllapitokohde ypk
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = ypk.id
                                     AND pi.poistettu IS NOT TRUE
  LEFT JOIN paikkausilmoitus pai ON pai.paikkauskohde = ypk.id
                                    AND pai.poistettu IS NOT TRUE
  LEFT JOIN urakka u ON ypk.urakka = u.id
  LEFT JOIN laatupoikkeama lp ON lp.yllapitokohde = ypk.id
  LEFT JOIN sanktio s ON s.laatupoikkeama = lp.id
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
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
  ypka.kohde_valmis;

-- name: hae-tiemerkintaurakalle-osoitetut-yllapitokohteet
-- Hakee urakan sopimuksen kaikki yllapitokohteet ja niihin liittyvät ilmoitukset
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
  ypk.tr_kaista        AS "tr-kaista",
  ypk.yhaid            AS "yha-id",
  ypk.yha_kohdenumero  AS "yha-kohdenumero"
FROM yllapitokohde ypk
WHERE
  ypk.suorittava_tiemerkintaurakka = :urakka
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
  ypko.tunnus,
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

-- name: luo-yllapitokohde<!
-- Luo uuden ylläpitokohteen
INSERT INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi,
                           tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
                           tr_ajorata, tr_kaista, keskimaarainen_vuorokausiliikenne,
                           yllapitoluokka, sopimuksen_mukaiset_tyot,
                           arvonvahennykset, bitumi_indeksi, kaasuindeksi, yllapitokohdetyyppi,
                           yllapitokohdetyotyyppi, vuodet)
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
        :sopimuksen_mukaiset_tyot,
        :arvonvahennykset,
        :bitumi_indeksi,
        :kaasuindeksi,
        :yllapitokohdetyyppi :: yllapitokohdetyyppi,
        :yllapitokohdetyotyyppi :: yllapitokohdetyotyyppi,
        :vuodet :: INTEGER []);

-- name: paivita-yllapitokohde!
-- Päivittää ylläpitokohteen
UPDATE yllapitokohde
SET
  kohdenumero                       = :kohdenumero,
  nimi                              = :nimi,
  tr_numero                         = :tr_numero,
  tr_alkuosa                        = :tr_alkuosa,
  tr_alkuetaisyys                   = :tr_alkuetaisyys,
  tr_loppuosa                       = :tr_loppuosa,
  tr_loppuetaisyys                  = :tr_loppuetaisyys,
  tr_ajorata                        = :tr_ajorata,
  tr_kaista                         = :tr_kaista,
  keskimaarainen_vuorokausiliikenne = :keskimaarainen_vuorokausiliikenne,
  yllapitoluokka                    = :yllapitoluokka,
  sopimuksen_mukaiset_tyot          = :sopimuksen_mukaiset_tyot,
  arvonvahennykset                  = :arvonvanhennykset,
  bitumi_indeksi                    = :bitumi_indeksi,
  kaasuindeksi                      = :kaasuindeksi
WHERE id = :id
      AND urakka = :urakka;

-- name: poista-yllapitokohde!
-- Poistaa ylläpitokohteen
UPDATE yllapitokohde
SET poistettu = TRUE
WHERE id = :id
      AND urakka = :urakka;

-- name: luo-yllapitokohdeosa<!
-- Luo uuden yllapitokohdeosan
INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tunnus, tr_numero, tr_alkuosa, tr_alkuetaisyys,
                              tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, toimenpide,
                              paallystetyyppi, raekoko, tyomenetelma, massamaara,
                              ulkoinen_id, sijainti)
VALUES (:yllapitokohde,
  :nimi,
  :tunnus,
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
  (SELECT tierekisteriosoitteelle_viiva AS geom
   FROM tierekisteriosoitteelle_viiva(CAST(:tr_numero AS INTEGER),
                                      CAST(:tr_alkuosa AS INTEGER),
                                      CAST(:tr_alkuetaisyys AS INTEGER),
                                      CAST(:tr_loppuosa AS INTEGER),
                                      CAST(:tr_loppuetaisyys AS INTEGER))));

-- name: luo-yllapitokohdeosa-paallystysilmoituksen-apista<!
-- Luo uuden yllapitokohdeosan
INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tunnus, tr_numero, tr_alkuosa, tr_alkuetaisyys,
                              tr_loppuosa, tr_loppuetaisyys, ulkoinen_id, sijainti)
VALUES (:yllapitokohde,
  :nimi,
  :tunnus,
  :tr_numero,
  :tr_alkuosa,
  :tr_alkuetaisyys,
  :tr_loppuosa,
  :tr_loppuetaisyys,
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
  tunnus           = :tunnus,
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
  sijainti         = (SELECT tierekisteriosoitteelle_viiva AS geom
                      FROM tierekisteriosoitteelle_viiva(CAST(:tr_numero AS INTEGER),
                                                         CAST(:tr_alkuosa AS INTEGER),
                                                         CAST(:tr_alkuetaisyys AS INTEGER),
                                                         CAST(:tr_loppuosa AS INTEGER),
                                                         CAST(:tr_loppuetaisyys AS INTEGER)))
WHERE id = :id
      AND yllapitokohde IN (SELECT id
                            FROM yllapitokohde
                            WHERE urakka = :urakka);

-- name: poista-yllapitokohdeosa!
-- Poistaa ylläpitokohdeosan
UPDATE yllapitokohdeosa
SET poistettu = TRUE
WHERE id = :id
      AND yllapitokohde IN (SELECT id
                            FROM yllapitokohde
                            WHERE urakka = :urakka);

-- name: hae-paallystysurakan-aikataulu
-- Hakee päällystysurakan kohteiden aikataulutiedot
SELECT
  ypk.id,
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
  LEFT JOIN tietyoilmoitus tti ON ypk.id = tti.yllapitokohde
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
  ypk.kohdenumero,
  ypk.nimi,
  ypk.urakka,
  ypk.sopimus,
  ypka.paallystys_alku      AS "aikataulu-paallystys-alku",
  ypka.paallystys_loppu     AS "aikataulu-paallystys-loppu",
  ypka.tiemerkinta_takaraja AS "aikataulu-tiemerkinta-takaraja",
  ypka.tiemerkinta_alku     AS "aikataulu-tiemerkinta-alku",
  ypka.tiemerkinta_loppu    AS "aikataulu-tiemerkinta-loppu",
  ypka.kohde_valmis         AS "aikataulu-kohde-valmis",
  ypka.muokattu             AS "aikataulu-muokattu",
  ypka.muokkaaja            AS "aikataulu-muokkaaja",
  ypka.valmis_tiemerkintaan AS "valmis-tiemerkintaan",
  ypk.tr_numero             AS "tr-numero",
  ypk.tr_alkuosa            AS "tr-alkuosa",
  ypk.tr_alkuetaisyys       AS "tr-alkuetaisyys",
  ypk.tr_loppuosa           AS "tr-loppuosa",
  ypk.tr_loppuetaisyys      AS "tr-loppuetaisyys",
  ypk.tr_ajorata            AS "tr-ajorata",
  ypk.tr_kaista             AS "tr-kaista",
  ypk.yllapitoluokka,
  tti.id                    AS "tietyoilmoitus-id"
FROM yllapitokohde ypk
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
  LEFT JOIN tietyoilmoitus tti ON ypk.id = tti.yllapitokohde
WHERE
  ypk.suorittava_tiemerkintaurakka = :suorittava_tiemerkintaurakka
  AND (:vuosi :: INTEGER IS NULL OR (cardinality(ypk.vuodet) = 0
                                     OR ypk.vuodet @> ARRAY [:vuosi] :: INT []))
  AND ypk.poistettu IS NOT TRUE
ORDER BY ypka.paallystys_alku;

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
WHERE (loppupvm IS NULL OR loppupvm >= NOW())
      AND tyyppi = 'tiemerkinta' :: urakkatyyppi;

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

-- name: tallenna-yllapitokohteen-suorittava-tiemerkintaurakka!
-- Tallentaa ylläpitokohteen aikataulun
UPDATE yllapitokohde
SET
  suorittava_tiemerkintaurakka = :suorittava_tiemerkintaurakka
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
  ypk.tr_alkuetaisyys    AS "tr-alkuetaisyys",
  ypk.tr_loppuosa        AS "tr-loppuosa",
  ypk.tr_loppuetaisyys   AS "tr-loppuetaisyys",
  ypka.tiemerkinta_loppu AS "aikataulu-tiemerkinta-loppu",
  pu.id                  AS "paallystysurakka-id",
  pu.nimi                AS "paallystysurakka-nimi",
  pu.sampoid             AS "paallystysurakka-sampo-id",
  tu.id                  AS "tiemerkintaurakka-id",
  tu.nimi                AS "tiemerkintaurakka-nimi",
  tu.sampoid             AS "tiemerkintaurakka-sampo-id"
FROM yllapitokohde ypk
  JOIN urakka pu ON ypk.urakka = pu.id
  JOIN urakka tu ON ypk.suorittava_tiemerkintaurakka = tu.id
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
WHERE ypk.id IN (:idt);

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
  sopimus,
  kohdenumero,
  nimi,
  sopimuksen_mukaiset_tyot,
  arvonvahennykset,
  bitumi_indeksi,
  kaasuindeksi,
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
  yhatunnus,
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
  tr_loppuetaisyys = :tr_loppuetaisyys
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
  tiemerkinta_takaraja = :aikataulu_tiemerkinta_takaraja,
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
  tiemerkinta_alku     = :tiemerkinta_alku,
  tiemerkinta_loppu    = :tiemerkinta_loppu,
  tiemerkinta_takaraja = :aikataulu_tiemerkinta_takaraja,
  muokattu             = NOW(),
  muokkaaja            = :muokkaaja
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

-- name: hae-yllapitokohteen-tiedot-tietyoilmoitukselle
SELECT
  ypk.id,
  ypk.tr_numero        AS "tr-numero",
  ypk.tr_alkuosa       AS "tr-alkuosa",
  ypk.tr_alkuetaisyys  AS "tr-alkuetaisyys",
  ypk.tr_loppuosa      AS "tr-loppuosa",
  ypk.tr_loppuetaisyys AS "tr-loppuetaisyys",
  ypkat.kohde_alku     AS alku,
  ypkat.kohde_valmis   AS loppu,
  u.id                 AS "urakka-id",
  u.nimi               AS "urakka-nimi",
  u.sampoid            AS "urakka-sampo-id",
  urk.id               AS "urakoitsija-id",
  urk.nimi             AS "urakoitsija-nimi",
  ely.id               AS "tilaaja-id",
  ely.nimi             AS "tilaaja-nimi"
FROM yllapitokohde ypk
  JOIN yllapitokohteen_aikataulu ypkat ON ypk.id = ypkat.yllapitokohde
  JOIN urakka u ON ypk.urakka = u.id
  JOIN organisaatio urk ON u.urakoitsija = urk.id
  JOIN organisaatio ely ON u.hallintayksikko = ely.id
WHERE ypk.id = :kohdeid;