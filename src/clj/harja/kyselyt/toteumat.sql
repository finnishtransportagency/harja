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
  tt.maara              AS tehtava_maara

FROM toteuma t
  LEFT JOIN kayttaja k ON k.id = t.luoja
  LEFT JOIN organisaatio o ON o.id = k.organisaatio
  JOIN toteuma_tehtava tt ON (tt.toteuma = t.id AND tt.poistettu IS NOT TRUE)
  JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
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

-- name: paivita-toteuma!
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
WHERE id = :id AND urakka = :urakka
AND poistettu IS NOT TRUE;

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
  poistettu           = FALSE
WHERE ulkoinen_id = :id AND urakka = :urakka
AND poistettu IS NOT TRUE;

-- name: luo-toteuma<!
-- Luo uuden toteuman.
INSERT
INTO toteuma
(urakka, sopimus, alkanut, paattynyt, tyyppi, luotu, luoja,
 poistettu, suorittajan_nimi, suorittajan_ytunnus, lisatieto, ulkoinen_id, reitti,
 tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, lahde)
VALUES (:urakka, :sopimus, :alkanut, :paattynyt, :tyyppi :: toteumatyyppi, NOW(), :kayttaja,
                 FALSE, :suorittaja, :ytunnus, :lisatieto, :ulkoinen_id, :reitti,
        :numero, :alkuosa, :alkuetaisyys, :loppuosa, :loppuetaisyys, :lahde :: lahde);

-- name: poista-toteuma!
UPDATE toteuma
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE id IN (:id) AND poistettu IS NOT TRUE
AND poistettu IS NOT TRUE;

-- name: poista-toteumat-ulkoisilla-idlla-ja-luojalla!
UPDATE toteuma
SET muokattu = NOW(), muokkaaja = :kayttaja-id, poistettu = TRUE
WHERE ulkoinen_id IN (:ulkoiset-idt) AND luoja = :kayttaja-id AND poistettu IS NOT TRUE
AND poistettu IS NOT TRUE;

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
    WHERE ulkoinen_id = :ulkoinen_id AND luoja = :luoja);

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
      AND urakka = :urakka
      AND poistettu IS NOT TRUE;

-- name: paivita-toteuman-tehtava!
-- Päivittää toteuman tehtävän id:llä.
UPDATE toteuma_tehtava
SET toimenpidekoodi = :toimenpidekoodi, maara = :maara, poistettu = :poistettu,
  paivan_hinta      = :paivanhinta,
  indeksi           = (CASE WHEN :paivanhinta :: NUMERIC IS NULL
    THEN TRUE
                       ELSE FALSE
                       END)
WHERE id = :id
AND poistettu IS NOT TRUE;

-- name: poista-toteuman-tehtava!
-- Poistaa toteuman tehtävän
UPDATE toteuma_tehtava
SET poistettu = TRUE
WHERE id = :id
AND poistettu IS NOT TRUE;

-- name: merkitse-toteuman-maksuera-likaiseksi!
-- Merkitsee toteumaa vastaavan maksuerän likaiseksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE maksuera
SET likainen = TRUE
WHERE
  tyyppi = :tyyppi :: maksueratyyppi AND
  toimenpideinstanssi IN (SELECT tpi.id
                          FROM toimenpideinstanssi tpi
                            JOIN toimenpidekoodi emo ON emo.id = tpi.toimenpide
                            JOIN toimenpidekoodi tpk ON tpk.emo = emo.id
                          WHERE tpk.id = :toimenpidekoodi);

-- name: merkitse-toteumatehtavien-maksuerat-likaisiksi!
-- Merkitsee toteumaa vastaavan maksuerän likaiseksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE maksuera
SET likainen = TRUE
WHERE
  numero IN (SELECT m.numero
             FROM maksuera m
               JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
               JOIN toimenpidekoodi emo ON emo.id = tpi.toimenpide
               JOIN toimenpidekoodi tpk ON tpk.emo = emo.id
               JOIN toteuma_tehtava tt ON tt.toimenpidekoodi = tpk.id
               JOIN toteuma t ON t.id = tt.toteuma
             WHERE tt.id IN (:toteuma_tehtava_idt) AND t.tyyppi :: TEXT = m.tyyppi :: TEXT);

-- name: merkitse-toimenpideinstanssin-kustannussuunnitelma-likaiseksi!
-- Merkitsee erilliskustannuksia vastaavan maksuerän likaiseksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE maksuera
SET likainen = TRUE
WHERE
  tyyppi = 'muu' AND
  toimenpideinstanssi IN (SELECT id
                          FROM toimenpideinstanssi
                          WHERE id = :toimenpideinstanssi);

-- name: luo-reittipiste<!
-- Luo uuden reittipisteen
INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka, soratiehoitoluokka)
VALUES (:toteuma, :aika, NOW(), ST_MakePoint(:x, :y) :: POINT,
        hoitoluokka_pisteelle(ST_MakePoint(:x, :y) :: GEOMETRY, 'talvihoito' :: hoitoluokan_tietolajitunniste,
                              250 :: INTEGER),
        hoitoluokka_pisteelle(ST_MakePoint(:x, :y) :: GEOMETRY, 'soratie' :: hoitoluokan_tietolajitunniste,
                              250 :: INTEGER));

-- name: poista-reittipiste-toteuma-idlla!
-- Poistaa toteuman kaikki reittipisteet
DELETE FROM reittipiste
WHERE toteuma = :id;

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

-- name: luo-reitti_tehtava<!
-- Luo uuden reitin tehtävän
INSERT INTO reitti_tehtava (reittipiste, luotu, toimenpidekoodi, maara)
VALUES (:reittipiste, NOW(), :toimenpidekoodi, :maara);

-- name: poista-reitti_tehtava-reittipiste-idlla!
-- Poistaa reitin tehtävät
DELETE FROM reitti_tehtava
WHERE reittipiste = :id;

-- name: luo-reitti_materiaali<!
-- Luo uuden reitin materiaalin
INSERT INTO reitti_materiaali (reittipiste, luotu, materiaalikoodi, maara)
VALUES (:reittipiste, NOW(), :materiaalikoodi, :maara);

-- name: poista-reitti_materiaali-reittipiste-idlla!
-- Poistaa reitin materiaalit
DELETE FROM reitti_materiaali
WHERE reittipiste = :id;

-- name: hae-toteuman-reittipisteet-idlla
SELECT *
FROM reittipiste
WHERE toteuma = :id
ORDER BY aika ASC;

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
  :luoja,
  NOW(),
  :tr_numero,
  :tr_alkuosa,
  :tr_alkuetaisyys,
  :tr_loppuosa,
  :tr_loppuetaisyys,
  :tr_puoli,
  :tr_ajorata,
  :sijainti);

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
  yrita_tierekisteriosoite_pisteille2(
      alkupiste(t.reitti), loppupiste(t.reitti), 1) :: TEXT AS tierekisteriosoite
FROM toteuma_tehtava tt
  JOIN toteuma t ON tt.toteuma = t.id
  JOIN toimenpidekoodi tk ON tt.toimenpidekoodi = tk.id
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
SELECT
  rp.id       AS id,
  rp.aika     AS aika,
  rp.sijainti AS sijainti
FROM reittipiste rp
WHERE
  rp.toteuma = :toteuma_id
ORDER BY rp.aika ASC;

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
                      WHERE t.urakka = :urakka)
      AND poistettu IS NOT TRUE;

-- name: hae-urakan-varustetoteumat
SELECT
  vt.id,
  tunniste,
  toimenpide,
  tietolaji,
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
  tila
FROM varustetoteuma vt
  JOIN toteuma t ON vt.toteuma = t.id
  LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
  LEFT JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
WHERE urakka = :urakka
      AND sopimus = :sopimus
      AND alkanut >= :alkupvm
      AND alkanut <= :loppupvm
      AND (:rajaa_tienumerolla = FALSE OR vt.tr_numero = :tienumero)
      AND t.poistettu IS NOT TRUE
      AND tt.poistettu IS NOT TRUE
ORDER BY t.alkanut DESC
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
  yh.etunimi || ' ' || yh.sukunimi AS henkilo,
  o.nimi                           AS organisaatio,
  o.ytunnus                        AS yTunnus
FROM varustetoteuma vt
  JOIN yhteyshenkilo yh ON vt.luoja = yh.id
  JOIN organisaatio o ON yh.organisaatio = o.id
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
SET lahetetty_tierekisteriin = TRUE
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
WHERE id = :id
AND poistettu IS NOT TRUE;

-- name: paivita-toteuman-reitti<!
UPDATE toteuma
SET reitti = :reitti
WHERE id = :id
AND poistettu IS NOT TRUE;

-- AJASTETTUJA TEHTÄVIÄ VARTEN

-- name: hae-reitittomat-mutta-reittipisteelliset-toteumat
-- Hakee toteumat, joille on olemassa reittipisteitä, mutta reittiä ei ole jostain syystä saatu tehtyä.
-- Käytetään ajastetussa tehtävässä
SELECT DISTINCT t.id
FROM toteuma t
  JOIN reittipiste rp ON t.id = rp.toteuma
WHERE t.reitti IS NULL;

-- name: hae-reitittomat-mutta-osoitteelliset-toteumat
-- Hakee toteumat, joille on tr-osoite, mutta reittiä ei ole saatu laskettua.
-- Käytetään ajastetussa tehtävässä
SELECT
  id,
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
SET lahetetty = now(), tila = :tila :: lahetyksen_tila
WHERE id = :id;

-- name: hae-epaonnistuneet-varustetoteuman-lahetykset
SELECT id
FROM varustetoteuma
WHERE tila = 'virhe';

-- name: suhteellinen-paikka-pisteiden-valissa
SELECT
  ST_LineLocatePoint(v.viiva ::geometry, ST_ClosestPoint (v.viiva ::geometry, :piste ::geometry) ::geometry) AS paikka
FROM
  (SELECT ST_MakeLine(:rp1 ::geometry, :rp2 ::geometry) AS viiva) v;

-- name: siirry-kokonaishintainen-toteuma
-- Palauttaa tiedot, joita tarvitaan kokonaishintaiseen toteumaan siirtymiseen ja
-- tarkistaa että käyttäjällä on oikeus urakkaan, johon toteuma kuuluu
SELECT t.alkanut, t.urakka AS "urakka-id", u.hallintayksikko AS "hallintayksikko-id",
       tt.toimenpidekoodi AS tehtava_toimenpidekoodi,
       tpk3.koodi AS tehtava_toimenpideinstanssi,
       hk.alkupvm AS aikavali_alku,
       hk.loppupvm AS aikavali_loppu
  FROM toteuma t
       JOIN urakka u ON t.urakka = u.id
       JOIN toteuma_tehtava tt ON tt.toteuma = t.id
       JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
       JOIN toimenpidekoodi tpk3 ON tpk.emo = tpk3.id
       JOIN urakan_hoitokaudet(t.urakka) hk ON (t.alkanut BETWEEN hk.alkupvm AND hk.loppupvm)
 WHERE t.id = :toteuma-id
   AND (:tarkista-urakka? = FALSE
        OR u.urakoitsija = :urakoitsija-id)
