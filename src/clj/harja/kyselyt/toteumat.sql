-- name: hae-urakan-toteumat
-- Listaa kaikki urakan toteumat
SELECT
  t.id,
  t.alkanut,
  t.paattynyt,
  t.tyyppi,
  t.suorittajan_nimi,
  t.suorittajan_ytunnus,
  t.lisatieto,
  t.luoja       AS luoja_id,
  o.nimi        AS organisaatio,
  k.kayttajanimi,
  k.jarjestelma AS jarjestelman_lisaama,
  (SELECT array_agg(concat(tt.id, '^', tpk.id, '^', tpk.nimi, '^', tt.maara))
   FROM toteuma_tehtava tt
     LEFT JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
   WHERE tt.toteuma = t.id
         AND tt.poistettu IS NOT TRUE)
                AS tehtavat
FROM toteuma t
  LEFT JOIN kayttaja k ON k.id = t.luoja
  LEFT JOIN organisaatio o ON o.id = k.organisaatio
WHERE
  t.urakka = :urakka
  AND t.sopimus = :sopimus
  AND t.alkanut >= :alkupvm
  AND t.alkanut <= :loppupvm
  AND t.tyyppi = :tyyppi :: toteumatyyppi
  AND t.poistettu IS NOT TRUE
GROUP BY t.id, t.alkanut, t.paattynyt, t.tyyppi, o.nimi, k.kayttajanimi, k.jarjestelma;

-- name: hae-urakan-toteuma
-- Listaa urakan toteuman id:llä
SELECT
  t.id,
  t.alkanut,
  t.paattynyt,
  t.tyyppi,
  t.suorittajan_nimi    AS suorittaja_nimi,
  t.suorittajan_ytunnus AS suorittaja_ytunnus,
  t.lisatieto,
  t.luoja               AS luojaid,
  ST_Length(t.reitti)   AS pituus,

  o.nimi                AS organisaatio,
  k.kayttajanimi,
  k.jarjestelma         AS jarjestelmanlisaama,
  t.reitti,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys,

  tt.id                 AS "tehtava_tehtava-id",
  tpk.id                AS "tehtava_tpk-id",
  tpk.nimi              AS tehtava_nimi,
  tpk.yksikko           AS tehtava_yksikko,
  tt.maara              AS tehtava_maara,
  tpi.id                AS tehtava_toimenpideinstanssi_id,
  tpi.nimi              AS tehtava_toimenpideinstanssi_nimi

FROM toteuma t
  LEFT JOIN kayttaja k ON k.id = t.luoja
  LEFT JOIN organisaatio o ON o.id = k.organisaatio
  JOIN toteuma_tehtava tt ON (tt.toteuma = t.id AND tt.poistettu IS NOT TRUE)
  JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
  LEFT JOIN toimenpidekoodi emo ON tpk.emo = emo.id
  LEFT JOIN toimenpideinstanssi tpi ON emo.id = tpi.toimenpide
                                       AND tpi.urakka = t.urakka
WHERE
  t.urakka = :urakka
  AND t.id = :toteuma
  AND t.poistettu IS NOT TRUE;

-- name: toteuma-jarjestelman-lisaama
SELECT k.jarjestelma AS jarjestelmanlisaama
FROM toteuma t
  LEFT JOIN kayttaja k ON k.id = t.luoja
WHERE t.id = :toteuma;

-- name: toteuman-urakka
SELECT t.urakka
FROM toteuma t
WHERE t.id = :toteuma;

-- name: erilliskustannuksen-urakka
SELECT t.urakka
FROM erilliskustannus t
WHERE t.id = :id;

-- name: toteuman-tyyppi
SELECT t.tyyppi
FROM toteuma t
WHERE t.id = :toteuma;

-- name: tehtavan-toteuma
SELECT tt.toteuma
FROM toteuma_tehtava tt
WHERE tt.id = :tehtava;

-- name: hae-toteumien-tehtavien-summat
-- Listaa urakan toteumien tehtävien määrien summat toimenpidekoodilla ryhmiteltynä.
SELECT
  x.tpk_id,
  x.maara,
  tk.nimi
FROM (SELECT
        toimenpidekoodi AS tpk_id,
        SUM(tt.maara)   AS maara
      FROM toteuma_tehtava tt
      WHERE tt.toteuma IN (SELECT id
                           FROM toteuma t
                           WHERE t.urakka = :urakka
                                 AND t.sopimus = :sopimus
                                 AND t.alkanut >= :alkanut
                                 AND t.alkanut <= :paattynyt
                                 AND t.tyyppi = :tyyppi :: toteumatyyppi
                                 AND t.poistettu IS NOT TRUE)
            AND tt.toimenpidekoodi IN (SELECT id
                                       FROM toimenpidekoodi tk
                                       WHERE (:toimenpide :: INTEGER IS NULL
                                              OR tk.emo = (SELECT toimenpide
                                                           FROM toimenpideinstanssi
                                                           WHERE id = :toimenpide))
                                             AND (:tehtava :: INTEGER IS NULL OR tk.id = :tehtava))
            AND tt.poistettu IS NOT TRUE
      GROUP BY toimenpidekoodi) x
  JOIN toimenpidekoodi tk ON x.tpk_id = tk.id
ORDER BY nimi;

-- name: hae-toteuman-toteuma-materiaalit-ja-tehtavat
-- Hakee toteuma_materiaalien ja tehtävien id:t. Hyödyllinen kun poistetaan toteuma.
SELECT
  tt.id AS tehtava_id,
  tm.id AS materiaali_id
FROM toteuma t
  LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
  LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
WHERE t.id IN (:id);

-- name: hae-urakan-toteuma-paivat
-- Hakee päivät tietyllä aikavälillä, jolle urakalla on toteumia.
SELECT DISTINCT date_trunc('day', alkanut) AS paiva
FROM toteuma
WHERE urakka = :urakka
      AND sopimus = :sopimus
      AND alkanut >= :alkupvm
      AND paattynyt <= :loppupvm
      AND poistettu IS NOT TRUE;

-- name: hae-urakan-tehtavat
-- Hakee tehtävät, joita annetulle urakalle voi kirjata.
SELECT
  id,
  nimi,
  yksikko
FROM toimenpidekoodi
WHERE taso = 4
      AND poistettu IS NOT TRUE
      AND emo IN (SELECT toimenpide
                  FROM toimenpideinstanssi
                  WHERE urakka = :urakka);

-- name: hae-urakan-ja-sopimuksen-toteutuneet-tehtavat
-- Hakee urakan tietyntyyppiset toteutuneet tehtävät
SELECT
  tt.id                           AS tehtava_id,
  tt.toteuma                      AS toteuma_id,
  tt.toimenpidekoodi,
  tt.maara,
  t.tyyppi,
  t.alkanut,
  t.paattynyt,
  t.suorittajan_nimi,
  t.suorittajan_ytunnus,
  t.lisatieto,
  (SELECT nimi
   FROM toimenpidekoodi tpk
   WHERE id = tt.toimenpidekoodi) AS toimenpide
FROM toteuma_tehtava tt
  INNER JOIN toteuma t ON tt.toteuma = t.id
                          AND urakka = :urakka
                          AND sopimus = :sopimus
                          AND alkanut >= :alkupvm
                          AND paattynyt <= :loppupvm
                          AND tyyppi = :tyyppi :: toteumatyyppi
                          AND tt.poistettu IS NOT TRUE
                          AND t.poistettu IS NOT TRUE;

-- name: hae-urakan-toteutuneet-tehtavat-kuukausiraportille
-- Hakee urakan tietyntyyppiset toteutuneet tehtävät
SELECT
  tt.id                           AS id,
  tt.maara                        AS toteutunut_maara,
  t.lisatieto                     AS lisatieto,
  t.alkanut,
  (SELECT nimi
   FROM toimenpidekoodi tpk
   WHERE id = tt.toimenpidekoodi) AS nimi,
  (SELECT id
   FROM toimenpidekoodi tpk
   WHERE id = tt.toimenpidekoodi) AS toimenpidekoodi_id
FROM toteuma_tehtava tt
  INNER JOIN toteuma t ON tt.toteuma = t.id
                          AND urakka = :urakka
                          AND alkanut >= :alkupvm
                          AND alkanut <= :loppupvm
                          AND tyyppi = :tyyppi :: toteumatyyppi
                          AND tt.poistettu IS NOT TRUE
                          AND t.poistettu IS NOT TRUE;

-- name: listaa-urakan-hoitokauden-toteumat-muut-tyot
-- Hakee urakan muutos-, lisä- ja äkilliset hoitotyötoteumat
SELECT
  tt.id, -- Jotta "sarakkeet vektoriin" toimii oikein
  tt.id              AS tehtava_id,
  tt.toteuma         AS toteuma_id,
  tt.toimenpidekoodi AS tehtava_toimenpidekoodi,
  tt.maara           AS tehtava_maara,
  tt.lisatieto       AS tehtava_lisatieto,
  tt.paivan_hinta    AS tehtava_paivanhinta,
  t.tyyppi,
  t.alkanut,
  t.paattynyt,
  t.suorittajan_nimi,
  t.suorittajan_ytunnus,
  t.lisatieto,
  tr_numero,
  tr_alkuetaisyys,
  tr_alkuosa,
  tr_loppuetaisyys,
  tr_loppuosa,
  reitti,
  l.id   as liite_id,
  l.nimi as liite_nimi,
  l.tyyppi as liite_tyyppi,
  l.koko as liite_koko,
  l.liite_oid as liite_oid,
  tpk.emo            AS tehtava_emo,
  tpk.nimi           AS tehtava_nimi,
  o.nimi             AS organisaatio,
  k.kayttajanimi,
  k.jarjestelma      AS jarjestelmasta
FROM toteuma_tehtava tt
  JOIN toimenpidekoodi tpk ON tpk.id = tt.toimenpidekoodi
  INNER JOIN toteuma t ON tt.toteuma = t.id
                          AND urakka = :urakka
                          AND sopimus = :sopimus
                          AND alkanut >= :alkupvm
                          AND alkanut <= :loppupvm
                          AND tyyppi IN ('akillinen-hoitotyo' :: toteumatyyppi,
                                         'lisatyo' :: toteumatyyppi,
                                         'muutostyo' :: toteumatyyppi,
                                         'vahinkojen-korjaukset' :: toteumatyyppi)
                          AND tt.poistettu IS NOT TRUE
                          AND t.poistettu IS NOT TRUE
  LEFT JOIN kayttaja k ON k.id = t.luoja
  LEFT JOIN toteuma_liite tl ON tl.toteuma = t.id
  LEFT JOIN liite l ON l.id = tl.liite
  LEFT JOIN organisaatio o ON o.id = k.organisaatio;

-- name: hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla
-- Hakee urakan tietyntyyppiset toteutuneet tehtävät tietyllä toimenpidekoodilla
SELECT
  tt.id                           AS tehtava_id,
  tt.toteuma                      AS toteuma_id,
  tt.toimenpidekoodi,
  tt.maara,
  t.tyyppi,
  t.alkanut,
  t.paattynyt,
  t.suorittajan_nimi,
  t.suorittajan_ytunnus,
  t.lisatieto,
  k.jarjestelma                   AS jarjestelmanlisaama,
  (SELECT nimi
   FROM toimenpidekoodi tpk
   WHERE id = tt.toimenpidekoodi) AS toimenpide,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys

FROM toteuma_tehtava tt
  INNER JOIN toteuma t ON tt.toteuma = t.id
                          AND urakka = :urakka
                          AND sopimus = :sopimus
                          AND alkanut >= :alkupvm
                          AND paattynyt <= :loppupvm
                          AND tyyppi = :tyyppi :: toteumatyyppi
                          AND toimenpidekoodi = :toimenpidekoodi
                          AND tt.poistettu IS NOT TRUE
                          AND t.poistettu IS NOT TRUE
  LEFT JOIN kayttaja k ON k.id = t.luoja
ORDER BY t.alkanut DESC
LIMIT 301;

-- name: paivita-toteuma<!
UPDATE toteuma
SET alkanut           = :alkanut,
  paattynyt           = :paattynyt,
  tyyppi              = :tyyppi :: toteumatyyppi,
  muokattu            = NOW(),
  muokkaaja           = :kayttaja,
  suorittajan_nimi    = :suorittaja,
  suorittajan_ytunnus = :ytunnus,
  lisatieto           = :lisatieto,
  tr_numero           = :numero,
  tr_alkuosa          = :alkuosa,
  tr_alkuetaisyys     = :alkuetaisyys,
  tr_loppuosa         = :loppuosa,
  tr_loppuetaisyys    = :loppuetaisyys,
  poistettu           = FALSE
WHERE id = :id AND urakka = :urakka;

-- name: paivita-toteuma-ulkoisella-idlla<!
UPDATE toteuma
SET alkanut           = :alkanut,
  paattynyt           = :paattynyt,
  muokattu            = NOW(),
  muokkaaja           = :kayttaja,
  suorittajan_nimi    = :suorittajan_nimi,
  suorittajan_ytunnus = :ytunnus,
  lisatieto           = :lisatieto,
  tyyppi              = :tyyppi :: toteumatyyppi,
  sopimus             = :sopimus,
  poistettu           = FALSE,
  tyokonetyyppi       = :tyokonetyyppi,
  tyokonetunniste     = :tyokonetunniste,
  tyokoneen_lisatieto = :tyokoneen-lisatieto
WHERE ulkoinen_id = :id AND urakka = :urakka AND luoja = :luoja;

-- name: luo-toteuma<!
-- Luo uuden toteuman.
INSERT
INTO toteuma
(urakka, sopimus, alkanut, paattynyt, tyyppi, luotu, luoja,
 poistettu, suorittajan_nimi, suorittajan_ytunnus, lisatieto, ulkoinen_id, reitti,
 tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, lahde,
 tyokonetyyppi, tyokonetunniste, tyokoneen_lisatieto)
VALUES (:urakka, :sopimus, :alkanut, :paattynyt, :tyyppi :: toteumatyyppi, NOW(), :kayttaja,
                 FALSE, :suorittaja, :ytunnus, :lisatieto, :ulkoinen_id, :reitti,
        :numero, :alkuosa, :alkuetaisyys, :loppuosa, :loppuetaisyys, :lahde :: lahde,
        :tyokonetyyppi, :tyokonetunniste, :tyokoneen-lisatieto);

-- name: poista-toteuma!
UPDATE toteuma
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE id IN (:id) AND poistettu IS NOT TRUE;

-- name: poista-toteuma-tehtava!
UPDATE toteuma_tehtava
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
    WHERE toteuma = :toteuma-id AND poistettu IS NOT TRUE;

-- name: poista-toteumat-ulkoisilla-idlla-ja-luojalla!
UPDATE toteuma
SET muokattu = NOW(), muokkaaja = :kayttaja-id, poistettu = TRUE
WHERE ulkoinen_id IN (:ulkoiset-idt) AND luoja = :kayttaja-id AND urakka = :urakka-id AND poistettu IS NOT TRUE;

-- name: hae-poistettavien-toteumien-alkanut-ulkoisella-idlla
SELECT alkanut
  FROM toteuma t
 WHERE ulkoinen_id IN (:ulkoiset-idt) AND luoja = :kayttaja-id AND urakka = :urakka-id AND poistettu IS NOT TRUE;

-- name: luo-tehtava<!
-- Luo uuden tehtävän toteumalle
INSERT
INTO toteuma_tehtava
(toteuma, toimenpidekoodi, maara, luotu, luoja, poistettu, paivan_hinta, indeksi)
VALUES (:toteuma, :toimenpidekoodi, :maara, NOW(), :kayttaja, FALSE, :paivanhinta,
        (CASE WHEN :paivanhinta :: NUMERIC IS NULL
          THEN TRUE
         ELSE FALSE
         END));

-- name: poista-toteuman-tehtavat!
UPDATE toteuma_tehtava
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE toteuma = :id AND poistettu IS NOT TRUE;

-- name: poista-tehtava!
UPDATE toteuma_tehtava
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE id IN (:id) AND poistettu IS NOT TRUE;

-- name: onko-olemassa-ulkoisella-idlla
-- Tarkistaa löytyykö toteumaa ulkoisella id:llä
SELECT EXISTS(
    SELECT ulkoinen_id
    FROM toteuma
    WHERE ulkoinen_id = :ulkoinen_id AND luoja = :luoja AND urakka = :urakka_id);

-- name: listaa-urakan-hoitokauden-erilliskustannukset
-- Listaa urakan erilliskustannukset
SELECT
  id,
  tyyppi,
  urakka,
  sopimus,
  toimenpideinstanssi,
  pvm,
  rahasumma,
  indeksin_nimi,
  lisatieto,
  luotu,
  luoja,
  kuukauden_indeksikorotus(pvm, indeksin_nimi, rahasumma, urakka)                          AS indeksikorjattuna,
  (SELECT korotettuna
   FROM laske_hoitokauden_asiakastyytyvaisyysbonus(urakka, pvm, indeksin_nimi, rahasumma)) AS "bonus-indeksikorjattuna"
FROM erilliskustannus
WHERE urakka = :urakka
      AND pvm >= :alkupvm AND pvm <= :loppupvm AND poistettu IS NOT TRUE;

-- name: listaa-urakan-maarien-toteumat
-- Listaa maarien toteumat
WITH toteumat AS (SELECT tk.id                      AS toimenpidekoodi_id,
                         t.id                       AS toteuma_id,
                         tt.id                      AS toteuma_tehtava_id,
                         tk.nimi                    AS tehtava,
                         tk.yksikko                 AS yksikko,
                         tr1.otsikko                AS otsikko,
                         t.alkanut                  AS alkanut,
                         tt.maara                   AS maara,
                         t.tyyppi                   as tyyppi,
                         CASE
                             WHEN EXTRACT(MONTH FROM t.alkanut) >= 10 THEN EXTRACT(YEAR FROM t.alkanut)
                             WHEN EXTRACT(MONTH FROM t.alkanut) <= 9 THEN (EXTRACT(YEAR FROM t.alkanut)-1)
                            END AS "hoitokauden-alkuvuosi"
                      FROM toteuma_tehtava tt,
                           toimenpidekoodi tk,
                           toteuma t,
                           tehtavaryhma tr1
                               JOIN tehtavaryhma tr2 ON tr2.id = tr1.emo
                               JOIN tehtavaryhma tr3 ON tr3.id = tr2.emo
                      WHERE tk.id = tt.toimenpidekoodi
                        AND t.id = tt.toteuma
                        AND t.poistettu IS NOT TRUE
                        AND t.urakka = :urakka
                        AND tr1.id = tk.tehtavaryhma
                        AND (:tehtavaryhma::TEXT IS NULL OR tr1.otsikko = :tehtavaryhma)
                        AND (:alkupvm::DATE IS NULL OR
                             t.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE))
-- Haetaan ne tehtävät, joilla on määrätoteuma
SELECT ut.id                      AS id,
       t.toteuma_id               AS toteuma_id,
       t.toteuma_tehtava_id       AS toteuma_tehtava_id,
       ut.urakka                  AS urakka,
       ut."hoitokauden-alkuvuosi" AS "hoitokauden-alkuvuosi",
       tk.nimi                    AS tehtava,
       tr1.otsikko                AS tehtavaryhma,
       ut.maara                   AS suunniteltu_maara,
       t.maara                    AS toteutunut,
       t.alkanut                  AS toteuma_aika,
       t.tyyppi                   AS tyyppi,
       tk.yksikko                 AS yksikko
    FROM urakka_tehtavamaara ut
             LEFT JOIN toteumat t
                       ON t.toimenpidekoodi_id = ut.tehtava AND t."hoitokauden-alkuvuosi" = ut."hoitokauden-alkuvuosi",
         toimenpidekoodi tk,
         tehtavaryhma tr1
             JOIN tehtavaryhma tr2 ON tr2.id = tr1.emo
             JOIN tehtavaryhma tr3 ON tr3.id = tr2.emo

    WHERE ut.tehtava = tk.id
      AND tr1.id = tk.tehtavaryhma

      AND ut.urakka = :urakka
      AND (:tehtavaryhma::TEXT IS NULL OR tr1.otsikko = :tehtavaryhma)
      AND (:alkupvm::DATE IS NULL OR (ut."hoitokauden-alkuvuosi" = CASE
                                                                       WHEN EXTRACT(MONTH FROM :alkupvm::DATE) >= 10
                                                                           THEN EXTRACT(YEAR FROM :alkupvm::DATE)
                                                                       WHEN EXTRACT(MONTH FROM :alkupvm::DATE) <= 9
                                                                           THEN (EXTRACT(YEAR FROM :alkupvm::DATE) - 1) END AND
                                      (t.alkanut IS NULL OR t.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE)))
UNION
-- ei union all, koska ei haluta duplikaatteja
-- Haetaan ne tehtävät, joilla ei ole määrää lisättynä
SELECT tk.id                      AS id,
       t.id                       AS toteuma_id,
       tt.id                      AS toteuma_tehtava_id,
       t.urakka                   AS urakka,
       EXTRACT(YEAR FROM alkanut) AS "hoitokauden-alkuvuosi",
       tk.nimi                    AS tehtava,
       tr1.otsikko                AS tehtavaryhma,
       -1                         AS suunniteltu_maara,
       tt.maara                   AS toteutunut,
       t.alkanut                  AS toteuma_aika,
       t.tyyppi                   AS tyyppi,
       tk.yksikko                 AS yksikko
    FROM toteuma_tehtava tt,
         toimenpidekoodi tk,
         toteuma t,
         tehtavaryhma tr1
             JOIN tehtavaryhma tr2 ON tr2.id = tr1.emo
             JOIN tehtavaryhma tr3 ON tr3.id = tr2.emo
    WHERE tk.id = tt.toimenpidekoodi
      AND tk.id NOT IN (SELECT ut.tehtava
                            FROM urakka_tehtavamaara ut
                            WHERE ut.urakka = :urakka
                              AND (:alkupvm::DATE IS NULL OR
                                   ut.luotu BETWEEN :alkupvm::DATE AND :loppupvm::DATE))
      AND t.id = tt.toteuma
      AND t.poistettu IS NOT TRUE
      AND t.urakka = :urakka
      AND tr1.id = tk.tehtavaryhma
      AND (:tehtavaryhma::TEXT IS NULL OR tr1.otsikko = :tehtavaryhma)
      AND (:alkupvm::DATE IS NULL OR
           t.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE);

-- name: hae-maarien-toteuma
-- Hae yksittäinen toteuma muokkaukseen
SELECT t.id        AS toteuma_id,
       EXTRACT(YEAR FROM alkanut) AS "hoitokauden-alkuvuosi",
       tk.nimi                    AS tehtava,
       tk.id                      AS tehtava_id,
       tt.maara                   AS toteutunut,
       t.alkanut                  AS toteuma_aika,
       tk.yksikko                 AS yksikko,
       tr1.otsikko                AS toimenpide_otsikko,
       tr1.id                     AS toimenpide_id,
       tt.id                      AS toteuma_tehtava_id,
       tt.lisatieto               AS lisatieto,
       t.tyyppi                   AS tyyppi,
       t.tr_numero                as sijainti_numero,
       t.tr_alkuosa               as sijainti_alku,
       t.tr_alkuetaisyys          as sijainti_alkuetaisyys,
       t.tr_loppuosa              as sijainti_loppu,
       t.tr_loppuetaisyys         as sijainti_loppuetaisyys
    FROM toteuma_tehtava tt,
         toimenpidekoodi tk,
         toteuma t,
         tehtavaryhma tr1
             JOIN tehtavaryhma tr2 ON tr2.id = tr1.emo
             JOIN tehtavaryhma tr3 ON tr3.id = tr2.emo
    WHERE t.id = :id
      AND tk.id = tt.toimenpidekoodi
      AND t.id = tt.toteuma
      AND t.poistettu IS NOT TRUE
      AND tr1.id = tk.tehtavaryhma;

-- name: hae-akillinen-toteuma
-- Hae yksittäinen äkillinen hoitottyö toteuma muokkaukseen
SELECT t.id        AS toteuma_id,
       EXTRACT(YEAR FROM alkanut) AS "hoitokauden-alkuvuosi",
       tk.nimi                    AS tehtava,
       tk.id                      AS tehtava_id,
       tt.maara                   AS toteutunut,
       t.alkanut                  AS toteuma_aika,
       tk.yksikko                 AS yksikko,
       tr.otsikko                AS toimenpide_otsikko,
       tr.id                     AS toimenpide_id,
       tt.id                      AS toteuma_tehtava_id,
       tt.lisatieto                AS lisatieto,
       t.tyyppi                   AS tyyppi
    FROM toteuma_tehtava tt,
         toimenpidekoodi tk,
         toteuma t,
         tehtavaryhma tr
    WHERE t.id = :id
      AND tk.id = tt.toimenpidekoodi
      AND t.id = tt.toteuma
      AND t.poistettu IS NOT TRUE
      AND tr.id = tk.tehtavaryhma;


-- name: listaa-urakan-toteutumien-toimenpiteet
-- Listaa kaikki toimenpiteet (tehtäväryhmät) määrien toteumille. Ehtona toimii emo is null ja tyyppi 'ylataso'
SELECT DISTINCT ON (tr.otsikko) tr.otsikko AS otsikko, tr.id
    FROM tehtavaryhma tr
    WHERE tr.emo IS NULL
      AND tr.tyyppi = 'ylataso'
    ORDER BY otsikko ASC, tr.id ASC;

-- name: listaa-maarien-toteumien-toimenpiteiden-tehtavat
-- Listaa kaikki tehtävät tehtäväryhmän perusteella määrien toteumille. Äkillisille hoitotöille
-- on ihan oma tehtäväryhmä ja tätä ei voida käyttää siihen
SELECT tk.id AS id, tk.nimi AS tehtava, tk.yksikko AS yksikko
    FROM toimenpidekoodi tk,
         tehtavaryhma tr1
             JOIN tehtavaryhma tr2 ON tr2.id = tr1.emo
             JOIN tehtavaryhma tr3 ON tr3.id = tr2.emo
    WHERE tr1.id = tk.tehtavaryhma AND tk.taso = 4 AND kasin_lisattava_maara = true
      AND (:tehtavaryhma::TEXT IS NULL OR tr1.otsikko = :tehtavaryhma);

-- name: listaa-akillisten-hoitotoiden-toimenpiteiden-tehtavat
SELECT  tk.id AS id,
        tr.nimi AS tehtava,
        tk.yksikko AS yksikko
    FROM toimenpidekoodi tk,
         tehtavaryhma tr
    WHERE (tr.nimi like '%Äkilliset hoitotyöt%'
      or tr.nimi like '%Tilaajan rahavaraus%'
      or tr.nimi like '%Vahinkojen korjaukset%')
      AND tk.tehtavaryhma = tr.id;

-- name: listaa-lisatoiden-tehtavat
SELECT  tk.id AS id,
        tk.nimi as tehtava,
        tk.yksikko AS yksikko
FROM toimenpidekoodi tk,
     tehtavaryhma tr
WHERE tk.nimi like '%Lisätyö%'
  AND tk.tehtavaryhma = tr.id;

-- name: luo-erilliskustannus<!
-- Listaa urakan erilliskustannukset
INSERT
INTO erilliskustannus
(tyyppi, urakka, sopimus, toimenpideinstanssi, pvm,
 rahasumma, indeksin_nimi, lisatieto, luotu, luoja)
VALUES (:tyyppi :: erilliskustannustyyppi, :urakka, :sopimus, :toimenpideinstanssi, :pvm,
        :rahasumma, :indeksin_nimi, :lisatieto, NOW(), :luoja);

-- name: paivita-erilliskustannus!
-- Päivitä erilliskustannus
UPDATE erilliskustannus
SET tyyppi            = :tyyppi :: erilliskustannustyyppi, urakka = :urakka, sopimus = :sopimus,
  toimenpideinstanssi = :toimenpideinstanssi,
  pvm                 = :pvm,
  rahasumma           = :rahasumma, indeksin_nimi = :indeksin_nimi, lisatieto = :lisatieto, muokattu = NOW(),
  muokkaaja           = :muokkaaja,
  poistettu           = :poistettu
WHERE id = :id
      AND urakka = :urakka;

-- name: paivita-toteuman-tehtava!
-- Päivittää toteuman tehtävän id:llä.
UPDATE toteuma_tehtava
SET toimenpidekoodi = :toimenpidekoodi, maara = :maara, poistettu = :poistettu,
  paivan_hinta      = :paivanhinta,
  indeksi           = (CASE WHEN :paivanhinta :: NUMERIC IS NULL
    THEN TRUE
                       ELSE FALSE
                       END)
WHERE id = :id;

-- name: poista-toteuman-tehtava!
-- Poistaa toteuman tehtävän
UPDATE toteuma_tehtava
SET poistettu = TRUE
WHERE id = :id;

-- name: merkitse-toteuman-maksuera-likaiseksi!
-- Merkitsee toteumaa vastaavan maksuerän likaiseksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE maksuera
SET likainen = TRUE,
    muokattu = current_timestamp
WHERE
  tyyppi = :tyyppi :: maksueratyyppi AND
  toimenpideinstanssi IN (SELECT tpi.id
                          FROM toimenpideinstanssi tpi
                            JOIN toimenpidekoodi emo ON emo.id = tpi.toimenpide
                            JOIN toimenpidekoodi tpk ON tpk.emo = emo.id
                          WHERE tpk.id = :toimenpidekoodi AND tpi.loppupvm > current_timestamp - INTERVAL '3 months');

-- name: merkitse-toteumatehtavien-maksuerat-likaisiksi!
-- Merkitsee toteumaa vastaavan maksuerän likaiseksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE maksuera
SET likainen = TRUE
WHERE
  numero IN (SELECT m.numero
             FROM maksuera m
               JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi AND tpi.loppupvm > current_timestamp - INTERVAL '3 months'
               JOIN toimenpidekoodi emo ON emo.id = tpi.toimenpide
               JOIN toimenpidekoodi tpk ON tpk.emo = emo.id
               JOIN toteuma_tehtava tt ON tt.toimenpidekoodi = tpk.id
               JOIN toteuma t ON t.id = tt.toteuma
             WHERE tt.id IN (:toteuma_tehtava_idt) AND t.tyyppi :: TEXT = m.tyyppi :: TEXT);

-- name: merkitse-toimenpideinstanssin-maksuera-likaiseksi!
-- Merkitsee erilliskustannuksia vastaavan maksuerän likaiseksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE maksuera
SET likainen = TRUE,
    muokattu = current_timestamp
WHERE
  tyyppi = 'muu' AND
  toimenpideinstanssi IN (SELECT id
                          FROM toimenpideinstanssi
                          WHERE id = :toimenpideinstanssi AND loppupvm > current_timestamp - INTERVAL '3 months');

-- name: hae-pisteen-hoitoluokat
SELECT hoitoluokka_pisteelle(ST_MakePoint(:x, :y) :: GEOMETRY,
                             'talvihoito'::hoitoluokan_tietolajitunniste,
			     250::INTEGER) AS talvihoitoluokka,
       hoitoluokka_pisteelle(ST_MakePoint(:x, :y) :: GEOMETRY,
                             'soratie'::hoitoluokan_tietolajitunniste,
			     250::INTEGER) AS soratiehoitoluokka;

-- name: luo-toteuma_tehtava<!
-- Luo uuden toteuman tehtävän
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, luoja, paivan_hinta,
                             lisatieto, indeksi)
VALUES (:toteuma, NOW(), :toimenpidekoodi, :maara, :luoja, :paivan_hinta, :lisatieto,
        (CASE WHEN :paivan_hinta :: NUMERIC IS NULL
          THEN TRUE
         ELSE FALSE
         END));

-- name: poista-toteuma_tehtava-toteuma-idlla!
-- Poistaa toteuman kaikki tehtävät
DELETE FROM toteuma_tehtava
WHERE toteuma = :id;

-- name: luo-toteuma-materiaali<!
-- Luo uuden toteuman materiaalin
INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, luoja)
VALUES (:toteuma, NOW(), :materiaalikoodi, :maara, :luoja);

-- name: poista-toteuma-materiaali-toteuma-idlla!
-- Poistaa toteuman materiaalit
DELETE FROM toteuma_materiaali
WHERE toteuma = :id;

-- name: paivita-varustetoteuman-tr-osoite!
-- Kysely piti katkaista kahtia, koska Yesql <0.5 tukee parametreja max 20
UPDATE varustetoteuma
SET
  tr_numero        = :tr_numero,
  tr_alkuosa       = :tr_alkuosa,
  tr_alkuetaisyys  = :tr_alkuetaisyys,
  tr_loppuosa      = :tr_loppuosa,
  tr_loppuetaisyys = :tr_loppuetaisyys,
  tr_puoli         = :tr_puoli,
  tr_ajorata       = :tr_ajorata
WHERE id = :id;

-- name: luo-varustetoteuma<!
-- Luo uuden varustetoteuman
INSERT INTO varustetoteuma (tunniste,
                            toteuma,
                            toimenpide,
                            tietolaji,
                            arvot,
                            karttapvm,
                            alkupvm,
                            loppupvm,
                            piiri,
                            kuntoluokka,
                            tierekisteriurakkakoodi,
                            luoja,
                            luotu,
                            tr_numero,
                            tr_alkuosa,
                            tr_alkuetaisyys,
                            tr_loppuosa,
                            tr_loppuetaisyys,
                            tr_puoli,
                            tr_ajorata,
                            sijainti)
VALUES (:tunniste,
  :toteuma,
  :toimenpide :: varustetoteuma_tyyppi,
  :tietolaji,
  :arvot,
  :karttapvm,
  :alkupvm,
  :loppupvm,
  :piiri,
  :kuntoluokka,
  :tierekisteriurakkakoodi,
  :kayttaja,
  NOW(),
  :tr_numero,
  :tr_alkuosa,
  :tr_alkuetaisyys,
  :tr_loppuosa,
  :tr_loppuetaisyys,
  :tr_puoli,
  :tr_ajorata,
  :sijainti);


-- name: paivita-varustetoteuma!
-- Päivittää annetun varustetoteuman
UPDATE varustetoteuma
SET
  tunniste                = :tunniste,
  toteuma                 = :toteuma,
  toimenpide              = :toimenpide :: VARUSTETOTEUMA_TYYPPI,
  tietolaji               = :tietolaji,
  arvot                   = :arvot,
  karttapvm               = :karttapvm,
  alkupvm                 = :alkupvm,
  loppupvm                = :loppupvm,
  piiri                   = :piiri,
  kuntoluokka             = :kuntoluokka,
  tierekisteriurakkakoodi = :tierekisteriurakkakoodi,
  tr_numero               = :tr_numero,
  tr_alkuosa              = :tr_alkuosa,
  tr_alkuetaisyys         = :tr_alkuetaisyys,
  tr_loppuosa             = :tr_loppuosa,
  tr_loppuetaisyys        = :tr_loppuetaisyys,
  tr_puoli                = :tr_puoli,
  tr_ajorata              = :tr_ajorata,
  sijainti                = :sijainti,
  muokkaaja               = :kayttaja,
  muokattu                = current_timestamp
WHERE id = :id;

-- name: poista-toteuman-varustetiedot!
DELETE FROM varustetoteuma
WHERE toteuma = :id;

-- name: hae-yksikkohintaisten-toiden-reitit
-- fetch-size: 64
-- row-fn: muunna-reitti
SELECT
  ST_Simplify(t.reitti, :toleranssi) AS reitti,
  tt.toimenpidekoodi                 AS tehtava_toimenpidekoodi,
  tpk.nimi                           AS tehtava_toimenpide
FROM toteuma_tehtava tt
  JOIN toteuma t ON tt.toteuma = t.id
  JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
WHERE
  t.urakka = :urakka-id
  AND (:toteuma-id :: INTEGER IS NULL OR t.id = :toteuma-id)
  AND t.sopimus = :sopimus-id
  AND t.alkanut >= :alkupvm
  AND t.alkanut <= :loppupvm
  AND ST_Intersects(t.envelope, ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax))
  AND t.tyyppi = 'yksikkohintainen' :: toteumatyyppi
  AND t.poistettu IS NOT TRUE
  AND (:toimenpide :: INTEGER IS NULL OR
       tpk.emo = (SELECT toimenpide
                  FROM toimenpideinstanssi
                  WHERE id = :toimenpide))
  AND (:tehtava :: INTEGER IS NULL OR tpk.id = :tehtava);

-- name: hae-kokonaishintaisten-toiden-reitit
-- fetch-size: 64
-- row-fn: muunna-reitti
SELECT
  ST_Simplify(t.reitti, :toleranssi) AS reitti,
  tk.nimi                            AS tehtava_toimenpide
FROM toteuma_tehtava tt
  JOIN toteuma t ON tt.toteuma = t.id
  JOIN toimenpidekoodi tk ON tt.toimenpidekoodi = tk.id
WHERE
  t.urakka = :urakka-id
  AND (:toteuma-id :: INTEGER IS NULL OR t.id = :toteuma-id)
  AND t.sopimus = :sopimus-id
  AND t.alkanut >= :alkupvm
  AND t.alkanut <= :loppupvm
  AND ST_Intersects(t.envelope, ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax))
  AND t.tyyppi = 'kokonaishintainen' :: toteumatyyppi
  AND t.poistettu IS NOT TRUE
  AND (:toimenpidekoodi :: INTEGER IS NULL OR tk.id = :toimenpidekoodi);

-- name: hae-toteumien-tiedot-pisteessa
-- Hakee klikkauspisteessä olevien (valitun toimenpiteen) toteumien
-- tiedot infopaneelissa näytettäväksi.
SELECT
  t.id,
  t.alkanut,
  t.paattynyt,
  t.suorittajan_nimi                                        AS suorittaja_nimi,
  tk.nimi                                                   AS tehtava_toimenpide,
  tt.maara                                                  AS tehtava_maara,
  tk.yksikko                                                AS tehtava_yksikko,
  tt.toteuma                                                AS tehtava_id,
  tk.nimi                                                   AS toimenpide,
  mk.nimi AS materiaalitoteuma_materiaali_nimi,
  mk.yksikko AS materiaalitoteuma_materiaali_yksikko,
  tm.maara AS materiaalitoteuma_maara,
  tm.id AS materiaalitoteuma_id,
  yrita_tierekisteriosoite_pisteille2(
      alkupiste(t.reitti), loppupiste(t.reitti), 1) :: TEXT AS tierekisteriosoite
FROM toteuma_tehtava tt
  JOIN toteuma t ON tt.toteuma = t.id
  JOIN toimenpidekoodi tk ON tt.toimenpidekoodi = tk.id
  LEFT JOIN toteuma_materiaali tm ON t.id = tm.toteuma AND tm.poistettu IS NOT TRUE
  LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
WHERE
  t.urakka = :urakka-id
  AND (:toteuma-id :: INTEGER IS NULL OR t.id = :toteuma-id)
  AND t.sopimus = :sopimus-id
  AND t.alkanut >= :alkupvm
  AND t.alkanut <= :loppupvm
  AND ST_Distance(t.reitti, ST_MakePoint(:x, :y)) < :toleranssi
  AND t.tyyppi = :tyyppi :: toteumatyyppi
  AND t.poistettu IS NOT TRUE
  AND (:toimenpidekoodi :: INTEGER IS NULL OR tk.id = :toimenpidekoodi);

-- name: hae-kokonaishintaisen-toteuman-reitti
SELECT
  mk.nimi            AS materiaali_nimi,
  tm.maara           AS materiaali_maara,
  tt.toteuma         AS toteumaid,
  t.alkanut          AS alkanut,
  t.paattynyt        AS paattynyt,
  t.reitti,
  t.suorittajan_nimi AS suorittaja_nimi,
  t.lisatieto        AS lisatieto,
  tk.nimi            AS tehtava_toimenpide,
  tt.maara           AS tehtava_maara,
  tk.id              AS tehtava_id
FROM toteuma_tehtava tt
  JOIN toteuma t ON tt.toteuma = t.id
  JOIN toimenpidekoodi tk ON tt.toimenpidekoodi = tk.id
  LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
  LEFT JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
  LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
WHERE
  t.urakka = :urakkaid
  AND t.sopimus = :sopimusid
  AND t.id = :toteumaid
  AND t.tyyppi = 'kokonaishintainen' :: toteumatyyppi
  AND t.poistettu IS NOT TRUE;

-- name: hae-urakan-kokonaishintaiset-toteumat-paivakohtaisina-summina
SELECT
  x.pvm,
  x.toimenpidekoodi,
  x.maara,
  x.pituus,
  k.jarjestelma AS jarjestelmanlisaama,
  tk.nimi       AS nimi,
  tk.yksikko    AS yksikko
FROM -- Haetaan toteuma tehtävät summattuna
  (SELECT
     t.alkanut :: DATE        AS pvm,
     tt.toimenpidekoodi,
     SUM(tt.maara)            AS maara,
     SUM(ST_Length(t.reitti)) AS pituus,
     tt.luoja
   FROM toteuma_tehtava tt
     JOIN -- Haetaan ensin vain toteumat, jotka osuvat filttereihin
     -- tämän avulla planner tajuaa käyttää toteuma_tehtavan toteuma indeksiä
     (SELECT
        t.alkanut,
        t.id,
        t.reitti
      FROM toteuma t
      WHERE t.urakka = :urakkaid
            AND t.sopimus = :sopimusid
            AND t.alkanut >= :alkupvm
            AND t.alkanut <= :loppupvm
            AND t.tyyppi = 'kokonaishintainen' :: toteumatyyppi
            AND t.poistettu IS NOT TRUE) t ON t.id = tt.toteuma
   WHERE tt.poistettu IS NOT TRUE
         AND tt.toimenpidekoodi IN (SELECT id
                                    FROM toimenpidekoodi tk
                                    WHERE (:toimenpide :: INTEGER IS NULL OR
                                           tk.emo = (SELECT toimenpide
                                                     FROM toimenpideinstanssi
                                                     WHERE id = :toimenpide))
                                          AND (:tehtava :: INTEGER IS NULL OR tk.id = :tehtava))
   GROUP BY pvm, toimenpidekoodi, luoja) x
  JOIN -- Otetaan mukaan käyttäjät järjestelmätietoa varten
  kayttaja k ON x.luoja = k.id
  JOIN -- Otetaan mukaan toimenpidekoodi nimeä ja yksikköä varten
  toimenpidekoodi tk ON x.toimenpidekoodi = tk.id
ORDER BY pvm DESC

-- name: hae-toteuman-tehtavat
SELECT
  tt.id              AS id,
  tt.toimenpidekoodi AS toimenpidekoodi,
  tk.nimi            AS nimi,
  tt.maara           AS maara,
  tk.yksikko         AS yksikko
FROM toteuma_tehtava tt
  INNER JOIN toimenpidekoodi tk
    ON tk.id = tt.toimenpidekoodi
WHERE
  tt.toteuma = :toteuma_id AND tt.poistettu IS NOT TRUE;

-- name: hae-toteuman-reittipisteet
SELECT rp.aika     AS aika,
       rp.sijainti AS sijainti,
       rp.ordinality AS id
  FROM toteuma t
       JOIN toteuman_reittipisteet tr ON tr.toteuma = t.id
       JOIN LATERAL unnest(reittipisteet) WITH ORDINALITY rp ON TRUE
 WHERE t.id = :toteuma_id

-- name: hae-toteuman-reitti-ja-tr-osoite
SELECT
  tr_numero,
  tr_alkuetaisyys,
  tr_alkuosa,
  tr_loppuetaisyys,
  tr_loppuosa,
  reitti
FROM toteuma
WHERE id = :id;

-- name: paivita-toteuma-materiaali!
-- Päivittää toteuma materiaalin tiedot
UPDATE toteuma_materiaali
SET materiaalikoodi = :materiaali,
  maara             = :maara,
  muokkaaja         = :kayttaja,
  muokattu          = now()
WHERE id = :tmid
      AND toteuma IN (SELECT id
                      FROM toteuma t
                      WHERE t.urakka = :urakka);

-- name: hae-urakan-varustetoteumat
SELECT
  vt.id,
  tunniste,
  toimenpide,
  tietolaji,
  vt.luotu,
  vt.tr_numero        AS tierekisteriosoite_numero,
  vt.tr_alkuosa       AS tierekisteriosoite_alkuosa,
  vt.tr_alkuetaisyys  AS tierekisteriosoite_alkuetaisyys,
  vt.tr_loppuosa      AS tierekisteriosoite_loppuosa,
  vt.tr_loppuetaisyys AS tierekisteriosoite_loppuetaisyys,
  piiri,
  kuntoluokka,
  karttapvm,
  tr_puoli            AS puoli,
  tr_ajorata          AS ajorata,
  t.id                AS toteumaid,
  t.urakka            AS urakkaid,
  t.alkanut,
  t.paattynyt,
  t.tyyppi            AS toteumatyyppi,
  arvot,
  tierekisteriurakkakoodi,
  vt.sijainti         AS sijainti,
  t.id                AS toteuma_id,
  t.reitti            AS toteumareitti,
  tt.id               AS toteumatehtava_id,
  tt.toimenpidekoodi  AS toteumatehtava_toimenpidekoodi,
  tt.maara            AS toteumatehtava_maara,
  tpk.nimi            AS toteumatehtava_nimi,
  t.lisatieto,
  alkupvm,
  loppupvm,
  lahetetty,
  lahetysvirhe,
  tila,
  k.etunimi           AS "luojan-etunimi",
  k.sukunimi          AS "luojan-sukunimi"
FROM varustetoteuma vt
  JOIN toteuma t ON vt.toteuma = t.id
  LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
  LEFT JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
  left join kayttaja k on vt.luoja = k.id
WHERE urakka = :urakka
      AND sopimus = :sopimus
      AND alkanut >= :alkupvm
      AND alkanut <= :loppupvm
      AND (:rajaa_tienumerolla = FALSE OR vt.tr_numero = :tienumero)
      AND t.poistettu IS NOT TRUE
      AND tt.poistettu IS NOT TRUE
      AND (:tietolajit :: VARCHAR [] IS NULL OR
           vt.tietolaji = ANY (:tietolajit :: VARCHAR []))
ORDER BY vt.luotu DESC
LIMIT 501;

-- name: hae-kokonaishintaisen-toteuman-tiedot
-- Hakee urakan kokonaishintaiset toteumat annetun päivän ja toimenpidekoodin perusteella
-- tai suoraan toteuman id:lla.
SELECT
  t.id,
  t.luotu,
  t.alkanut,
  t.paattynyt,
  t.lisatieto,
  t.suorittajan_ytunnus AS suorittaja_ytunnus,
  t.suorittajan_nimi    AS suorittaja_nimi,
  k.jarjestelma,
  tt.maara              AS tehtava_maara,
  tt.id                 AS tehtava_id,
  tpk.yksikko           AS tehtava_yksikko,
  tpk.id                AS tehtava_toimenpidekoodi_id,
  tpk.nimi              AS tehtava_toimenpidekoodi_nimi,
  tpi.id                AS tehtava_toimenpideinstanssi_id,
  tpi.nimi              AS tehtava_toimenpideinstanssi_nimi,
  ST_Length(reitti)     AS pituus,
  t.tr_numero, t.tr_alkuosa, t.tr_alkuetaisyys, t.tr_loppuosa, t.tr_loppuetaisyys
FROM toteuma t
  JOIN kayttaja k ON t.luoja = k.id
                     AND t.poistettu IS NOT TRUE
  LEFT JOIN toteuma_tehtava tt ON t.id = tt.toteuma
                                  AND tt.poistettu IS NOT TRUE
  LEFT JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
  LEFT JOIN toimenpidekoodi emo ON tpk.emo = emo.id
  LEFT JOIN toimenpideinstanssi tpi ON emo.id = tpi.toimenpide
                                       AND tpi.urakka = t.urakka
WHERE
  t.urakka = :urakka
  AND (:toteuma :: INTEGER IS NULL OR t.id = :toteuma)
  AND (:pvm :: DATE IS NULL OR t.alkanut :: DATE = :pvm :: DATE)
  AND (:toimenpidekoodi :: INTEGER IS NULL OR tt.toimenpidekoodi = :toimenpidekoodi);

-- name: hae-varustetoteuma
SELECT
  vt.toimenpide,
  vt.toteuma,
  vt.karttapvm,
  vt.tunniste,
  vt.alkupvm,
  vt.loppupvm,
  vt.tietolaji,
  vt.arvot,
  vt.tr_numero,
  vt.tr_alkuosa,
  vt.tr_alkuetaisyys,
  vt.tr_loppuosa,
  vt.tr_loppuetaisyys,
  vt.tr_ajorata,
  vt.tr_puoli,
  vt.luotu,
  k.etunimi || ' ' || k.sukunimi AS henkilo,
  o.nimi                         AS organisaatio,
  o.ytunnus                      AS yTunnus
FROM varustetoteuma vt
  JOIN kayttaja k ON vt.luoja = k.id
  JOIN organisaatio o ON k.organisaatio = o.id
WHERE vt.id = :id;

-- name: hae-varustetoteuma-toteumalla
SELECT
  id,
  tunniste
FROM varustetoteuma
WHERE
  toteuma = :toteumaid
  AND (:tunniste :: TEXT IS NULL OR tunniste = :tunniste)
  AND tietolaji = :tietolaji
  AND toimenpide = :toimenpide :: varustetoteuma_tyyppi
  AND (:tr_numero :: INTEGER IS NULL OR tr_numero = :tr_numero)
  AND (:tr_aosa :: INTEGER IS NULL OR tr_alkuosa = :tr_aosa)
  AND (:tr_aet :: INTEGER IS NULL OR tr_alkuetaisyys = :tr_aet)
  AND (:tr_losa :: INTEGER IS NULL OR tr_loppuosa = :tr_losa)
  AND (:tr_let :: INTEGER IS NULL OR tr_loppuetaisyys = :tr_let)
  AND (:tr_ajorata :: INTEGER IS NULL OR tr_ajorata = :tr_ajorata)
  AND (:tr_puoli :: INTEGER IS NULL OR tr_puoli = :tr_puoli);

-- name: hae-varustetoteuman-lahetystiedot
SELECT lahetetty_tierekisteriin
FROM varustetoteuma
WHERE id = :id;

-- name: merkitse-varustetoteuma-lahetetyksi<!
UPDATE varustetoteuma
SET lahetetty_tierekisteriin = TRUE,
    lahetetty = now(),
    tila = :tila :: lahetyksen_tila,
    lahetysvirhe = :lahetysvirhe
WHERE id = :id;

-- name: varustetoteuman-toimenpiteelle-sijainti
SELECT sijainti
FROM tierekisteriosoitteelle_viiva(:tie :: INTEGER,
                                   :aosa :: INTEGER,
                                   :aet :: INTEGER,
                                   :losa :: INTEGER,
                                   :let :: INTEGER) AS sijainti;

-- name: paivita-toteuman-reitti!
UPDATE toteuma
SET reitti = :reitti
WHERE id = :id;

-- name: paivita-toteuman-reitti<!
UPDATE toteuma
SET reitti = :reitti
WHERE id = :id;

-- AJASTETTUJA TEHTÄVIÄ VARTEN

-- name: hae-reitittomat-mutta-reittipisteelliset-toteumat
-- Hakee toteumat, joille on olemassa reittipisteitä, mutta reittiä ei ole jostain syystä saatu tehtyä.
-- Käytetään ajastetussa tehtävässä
SELECT DISTINCT t.id
  FROM toteuma t
       JOIN toteuman_reittipisteet tr ON t.id = tr.toteuma
 WHERE t.reitti IS NULL;

-- name: hae-reitittomat-mutta-osoitteelliset-toteumat
-- Hakee toteumat, joille on tr-osoite, mutta reittiä ei ole saatu laskettua.
-- Käytetään ajastetussa tehtävässä
SELECT id,
       tr_numero        AS numero,
       tr_alkuosa       AS alkuosa,
       tr_alkuetaisyys  AS alkuetaisyys,
       tr_loppuosa      AS loppuosa,
       tr_loppuetaisyys AS loppuetaisyys
  FROM toteuma t
 WHERE reitti IS NULL
   AND t.tr_numero IS NOT NULL
   AND t.tr_alkuosa IS NOT NULL
   AND t.tr_alkuetaisyys IS NOT NULL;

-- name: merkitse-varustetoteuma-lahetetyksi!
UPDATE varustetoteuma
SET lahetetty = now(), tila = :tila :: lahetyksen_tila, lahetysvirhe = :lahetysvirhe
WHERE id = :id;

-- name: hae-epaonnistuneet-varustetoteuman-lahetykset
-- Palauttaa rivit, joiden lähetys on epäonnistunut ja
-- jotka on luotu tai joita on muokattu viimeisen viikon aikana
SELECT id
FROM varustetoteuma
WHERE tila = 'virhe'
  and ((luotu IS NOT NULL AND (EXTRACT(EPOCH FROM (current_timestamp - luotu)) < 604800)) OR
       (muokattu IS NOT NULL AND (EXTRACT(EPOCH FROM (current_timestamp - muokattu)) < 604800)));

-- name: suhteellinen-paikka-pisteiden-valissa
SELECT
  ST_LineLocatePoint(v.viiva ::geometry, ST_ClosestPoint (v.viiva ::geometry, :piste ::geometry) ::geometry) AS paikka
FROM
  (SELECT ST_MakeLine(:rp1 ::geometry, :rp2 ::geometry) AS viiva) v;

-- name: siirry-toteuma
-- Palauttaa tiedot, joita tarvitaan frontilla toteumaan siirtymiseen ja
-- tarkistaa että käyttäjällä on oikeus urakkaan, johon toteuma kuuluu
SELECT t.alkanut, t.urakka AS "urakka-id", u.hallintayksikko AS "hallintayksikko-id",
       t.tyyppi,
       tt.toimenpidekoodi AS tehtava_toimenpidekoodi,
       tpk3.koodi AS tehtava_toimenpideinstanssi,
       hk.alkupvm AS aikavali_alku,
       hk.loppupvm AS aikavali_loppu
  FROM toteuma t
       JOIN urakka u ON t.urakka = u.id
       LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
       LEFT JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
       LEFT JOIN toimenpidekoodi tpk3 ON tpk.emo = tpk3.id
       JOIN urakan_hoitokaudet(t.urakka) hk ON (t.alkanut BETWEEN hk.alkupvm AND hk.loppupvm)
 WHERE t.id = :toteuma-id
   AND (:tarkista-urakka? = FALSE
        OR u.urakoitsija = :urakoitsija-id);

-- name: tallenna-liite-toteumalle<!
INSERT INTO toteuma_liite (toteuma, liite) VALUES (:toteuma, :liite);

-- name: hae-toteuman-liitteet
SELECT
  l.id        AS id,
  l.tyyppi    AS tyyppi,
  l.koko      AS koko,
  l.nimi      AS nimi,
  l.liite_oid AS oid
FROM liite l
  JOIN toteuma_liite tl ON l.id = tl.liite
WHERE tl.toteuma = :toteumaid
ORDER BY l.luotu ASC;

-- name: hae-toteumien-reitit
SELECT
  id,
  reitti as sijainti
FROM toteuma
WHERE urakka = :urakka-id AND id IN (:idt);
