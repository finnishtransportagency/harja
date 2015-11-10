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
  AND t.paattynyt <= :loppupvm
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
  t.suorittajan_nimi,
  t.suorittajan_ytunnus,
  t.lisatieto,
  t.luoja       AS luojaid,
  o.nimi        AS organisaatio,
  k.kayttajanimi,
  k.jarjestelma AS jarjestelmanlisaama,
  rp.id         AS reittipiste_id,
  rp.aika       AS reittipiste_aika,
  rp.sijainti   AS reittipiste_sijainti,
  (SELECT array_agg(concat(tt.id, '^', tpk.id, '^', tpk.nimi, '^', tt.maara))
   FROM toteuma_tehtava tt
     LEFT JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
   WHERE tt.toteuma = t.id
         AND tt.poistettu IS NOT TRUE)
                AS tehtavat
FROM toteuma t
  LEFT JOIN kayttaja k ON k.id = t.luoja
  LEFT JOIN organisaatio o ON o.id = k.organisaatio
  LEFT JOIN reittipiste rp ON rp.toteuma = t.id
WHERE
  t.urakka = :urakka
  AND t.id = :toteuma
  AND t.poistettu IS NOT TRUE;

-- name: hae-toteumien-tehtavien-summat
-- Listaa urakan toteumien tehtävien määrien summat toimenpidekoodilla ryhmiteltynä.
SELECT
          toimenpidekoodi AS tpk_id,
          SUM(tt.maara)   AS maara,
  (SELECT nimi
   FROM toimenpidekoodi tpk
   WHERE tpk.id = tt.toimenpidekoodi)
FROM toteuma_tehtava tt
  JOIN toteuma t ON tt.toteuma = t.id
                    AND t.urakka = :urakka
                    AND sopimus = :sopimus
                    AND alkanut >= :alkanut
                    AND paattynyt <= :paattynyt
                    AND tyyppi = :tyyppi :: toteumatyyppi
                    AND tt.poistettu IS NOT TRUE
                    AND t.poistettu IS NOT TRUE
GROUP BY toimenpidekoodi;

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
  tt.id                           AS tehtava_id,
  tt.toteuma                      AS toteuma_id,
  tt.toimenpidekoodi              AS tehtava_toimenpidekoodi,
  tt.maara                        AS tehtava_maara,
  tt.lisatieto                    AS tehtava_lisatieto,
  tt.paivan_hinta                 AS tehtava_paivanhinta,
  t.tyyppi,
  t.alkanut,
  t.paattynyt,
  t.suorittajan_nimi,
  t.suorittajan_ytunnus,
  t.lisatieto,

  tpk.emo                         AS tehtava_emo,
  tpk.nimi                        AS tehtava_nimi,
  o.nimi                          AS organisaatio,
  k.kayttajanimi,
  k.jarjestelma                   AS jarjestelmasta,

  rp.id                           AS reittipiste_id,
  rp.aika                         AS reittipiste_aika,
  rp.sijainti                     AS reittipiste_sijainti,

  rt.id                           AS reittipiste_tehtava_id,
  rt.toimenpidekoodi              AS reittipiste_tehtava_toimenpidekoodi,
  rt.maara                        AS reittipiste_tehtava_maara,
  (SELECT nimi
   FROM toimenpidekoodi tpk
   WHERE id = tt.toimenpidekoodi) AS reittipiste_tehtava_toimenpide
FROM toteuma_tehtava tt
  JOIN toimenpidekoodi tpk ON tpk.id = tt.toimenpidekoodi
  INNER JOIN toteuma t ON tt.toteuma = t.id
                          AND urakka = :urakka
                          AND sopimus = :sopimus
                          AND alkanut >= :alkupvm
                          AND paattynyt <= :loppupvm
                          AND tyyppi IN ('akillinen-hoitotyo' :: toteumatyyppi,
                                         'lisatyo' :: toteumatyyppi,
                                         'muutostyo' :: toteumatyyppi,
                                         'vahinkojen-korjaukset' :: toteumatyyppi)
                          AND tt.poistettu IS NOT TRUE
                          AND t.poistettu IS NOT TRUE
  LEFT JOIN reittipiste rp ON t.id = rp.toteuma
  LEFT JOIN reitti_tehtava rt ON rp.id = rt.reittipiste
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
                          AND toimenpidekoodi = :toimenpidekoodi
                          AND tt.poistettu IS NOT TRUE
                          AND t.poistettu IS NOT TRUE;

-- name: hae-toteumat-historiakuvaan
-- Hakee toteumat historiakuvaan toimenpidekoodin perusteella.
-- Hakua saatetaan suodattaa myös hallintayksikön, urakan, tai päivämäärän perusteella
SELECT
  t.id,
  t.urakka,
  t.sopimus,
  t.alkanut,
  t.paattynyt,
  t.tyyppi,
  t.lisatieto,
  t.suorittajan_ytunnus           AS suorittaja_ytunnus,
  t.suorittajan_nimi              AS suorittaja_nimi,
  t.ulkoinen_id                   AS ulkoinenid,

  tt.id                           AS tehtava_id,
  tt.toimenpidekoodi              AS tehtava_toimenpidekoodi,
  tt.maara                        AS tehtava_maara,
  tt.paivan_hinta                 AS tehtava_paivanhinta,
  tt.lisatieto                    AS tehtava_lisatieto,
  (SELECT nimi
   FROM toimenpidekoodi tpk
   WHERE id = tt.toimenpidekoodi) AS tehtava_toimenpide,

  tm.id                           AS materiaali_id,
  tm.maara                        AS materiaali_maara,

  mk.id                           AS materiaali_materiaali_id,
  mk.nimi                         AS materiaali_materiaali_nimi,
  mk.kohdistettava                AS materiaali_materiaali_kohdistettava,

  rp.id                           AS reittipiste_id,
  rp.aika                         AS reittipiste_aika,
  rp.sijainti                     AS reittipiste_sijainti,

  rt.id                           AS reittipiste_tehtava_id,
  rt.toimenpidekoodi              AS reittipiste_tehtava_toimenpidekoodi,
  rt.maara                        AS reittipiste_tehtava_maara,
  (SELECT nimi
   FROM toimenpidekoodi tpk
   WHERE id = tt.toimenpidekoodi) AS reittipiste_tehtava_toimenpide,


  rm.id                           AS reittipiste_materiaali_id,
  rm.materiaalikoodi              AS reittipiste_materiaali_materiaalikoodi,
  rm.maara                        AS reittipiste_materiaali_maara,
  mk.nimi                         AS reittipiste_materiaali_nimi
FROM toteuma_tehtava tt
  INNER JOIN toteuma t ON tt.toteuma = t.id
                          AND t.alkanut >= :alkupvm
                          AND t.paattynyt <= :loppupvm
                          AND tt.toimenpidekoodi IN (:toimenpidekoodit)
                          AND tt.poistettu IS NOT TRUE
                          AND t.poistettu IS NOT TRUE
  INNER JOIN reittipiste rp ON rp.toteuma = t.id
                               -- Haettavan reittipisteen pitää ensinnäkin mahtua kartalla näkyvälle alueelle
                               AND (st_contains((ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax)), rp.sijainti :: GEOMETRY))
  LEFT JOIN reitti_materiaali rm ON rm.reittipiste = rp.id
  LEFT JOIN reitti_tehtava rt ON rt.reittipiste = rp.id
  LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
                                     AND tm.poistettu IS NOT TRUE
  LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
WHERE t.urakka = :urakka OR :rajaa_urakalla IS FALSE
                            -- Sen jälkeen tarkastetaan hallintayksiköllä/urakalla suodattaminen
                            AND (
                              -- Joko ei suodateta HY:llä/urakalla
                              (:hallintayksikko_annettu IS FALSE AND :rajaa_urakalla IS FALSE) OR
                              -- tai suodatetaan vain HY:llä..
                              (:rajaa_urakalla IS FALSE AND
                               st_contains((SELECT alue
                                            FROM organisaatio
                                            WHERE id = :hallintayksikko),
                                           rp.sijainti :: GEOMETRY)) OR
                              -- Tai suodatetaan urakalla
                              (st_contains((SELECT alue
                                            FROM urakoiden_alueet
                                            WHERE id = :urakka),
                                           rp.sijainti :: GEOMETRY)))
ORDER BY rp.aika ASC;


-- name: paivita-toteuma!
UPDATE toteuma
SET alkanut           = :alkanut,
  paattynyt           = :paattynyt,
  muokattu            = NOW(),
  muokkaaja           = :kayttaja,
  suorittajan_nimi    = :suorittajan_nimi,
  suorittajan_ytunnus = :ytunnus,
  lisatieto           = :lisatieto,
  reitti              = :reitti
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
  reitti              = :reitti
WHERE ulkoinen_id = :id AND urakka = :urakka;


-- name: luo-toteuma<!
-- Luo uuden toteuman.
INSERT
INTO toteuma
(urakka, sopimus, alkanut, paattynyt, tyyppi, luotu, luoja,
 poistettu, suorittajan_nimi, suorittajan_ytunnus, lisatieto, ulkoinen_id, reitti)
VALUES (:urakka, :sopimus, :alkanut, :paattynyt, :tyyppi :: toteumatyyppi, NOW(), :kayttaja,
                 FALSE, :suorittaja, :tunnus, :lisatieto, :ulkoinen_id, :reitti);

-- name: poista-toteuma!
UPDATE toteuma
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE id IN (:id) AND poistettu IS NOT TRUE;

-- name: luo-tehtava<!
-- Luo uuden tehtävän toteumalle
INSERT
INTO toteuma_tehtava
(toteuma, toimenpidekoodi, maara, luotu, luoja, poistettu, paivan_hinta)
VALUES (:toteuma, :toimenpidekoodi, :maara, NOW(), :kayttaja, FALSE, :paivanhinta);

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
SELECT exists(
    SELECT toteuma.id
    FROM toteuma
    WHERE ulkoinen_id = :ulkoinen_id AND luoja = :luoja);

-- name: listaa-urakan-hoitokauden-erilliskustannukset
-- Listaa urakan erilliskustannukset
SELECT
  id,
  tyyppi,
  sopimus,
  toimenpideinstanssi,
  pvm,
  rahasumma,
  indeksin_nimi,
  lisatieto,
  luotu,
  luoja,
  kuukauden_indeksikorotus(pvm, indeksin_nimi, rahasumma) AS indeksikorjattuna
FROM erilliskustannus
WHERE sopimus IN (SELECT id
                  FROM sopimus
                  WHERE urakka = :urakka)
      AND pvm >= :alkupvm AND pvm <= :loppupvm AND poistettu = FALSE;

-- name: luo-erilliskustannus<!
-- Listaa urakan erilliskustannukset
INSERT
INTO erilliskustannus
(tyyppi, sopimus, toimenpideinstanssi, pvm,
 rahasumma, indeksin_nimi, lisatieto, luotu, luoja)
VALUES (:tyyppi :: erilliskustannustyyppi, :sopimus, :toimenpideinstanssi, :pvm,
        :rahasumma, :indeksin_nimi, :lisatieto, NOW(), :luoja);

-- name: paivita-erilliskustannus!
-- Päivitä erilliskustannus
UPDATE erilliskustannus
SET tyyppi  = :tyyppi :: erilliskustannustyyppi, sopimus = :sopimus, toimenpideinstanssi = :toimenpideinstanssi,
  pvm       = :pvm,
  rahasumma = :rahasumma, indeksin_nimi = :indeksin_nimi, lisatieto = :lisatieto, muokattu = NOW(),
  muokkaaja = :muokkaaja,
  poistettu = :poistettu
WHERE id = :id;

-- name: paivita-toteuman-tehtava!
-- Päivittää toteuman tehtävän id:llä.
UPDATE toteuma_tehtava
SET toimenpidekoodi = :toimenpidekoodi, maara = :maara, poistettu = :poistettu, paivan_hinta = :paivanhinta
WHERE id = :id;

-- name: poista-toteuman-tehtava!
-- Poistaa toteuman tehtävän
UPDATE toteuma_tehtava
SET poistettu = TRUE
WHERE id = :id;

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
INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, hoitoluokka)
VALUES (:toteuma, :aika, NOW(), ST_MakePoint(:x, :y) :: POINT,
        hoitoluokka_pisteelle(ST_MakePoint(:x, :y) :: GEOMETRY, :hoitoluokka, 250 :: INTEGER));

-- name: poista-reittipiste-toteuma-idlla!
-- Poistaa toteuman kaikki reittipisteet
DELETE FROM reittipiste
WHERE toteuma = :id;

-- name: luo-toteuma_tehtava<!
-- Luo uuden toteuman tehtävän
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, luoja, paivan_hinta, lisatieto)
VALUES (:toteuma, NOW(), :toimenpidekoodi, :maara, :luoja, :paivan_hinta, :lisatieto);

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
WHERE toteuma = :id;

-- name: luo-varustetoteuma<!
-- Luo uuden varustetoteuman
INSERT INTO varustetoteuma (tunniste,
                            toteuma,
                            toimenpide,
                            tietolaji,
                            arvot,
                            karttapvm,
                            tr_numero,
                            tr_alkuosa,
                            tr_loppuosa,
                            tr_loppuetaisyys,
                            tr_alkuetaisyys,
                            tr_puoli,
                            tr_ajorata,
                            alkupvm,
                            loppupvm,
                            piiri,
                            kuntoluokka,
                            tierekisteriurakkakoodi,
                            luoja,
                            luotu)
VALUES (
  :tunniste,
  :toteuma,
  :toimenpide :: varustetoteuma_tyyppi,
  :tietolaji,
  :arvot,
  :karttapvm,
  :tr_numero,
  :tr_alkuosa,
  :tr_loppuosa,
  :tr_loppuetaisyys,
  :tr_alkuetaisyys,
  :tr_puoli,
  :tr_ajorata,
  :alkupvm,
  :loppupvm,
  :piiri,
  :kuntoluokka,
  :tierekisteriurakkakoodi,
  :luoja,
  NOW());

-- name: poista-toteuman-varustetiedot!
DELETE FROM varustetoteuma
WHERE toteuma = :id;

-- name: hae-urakan-kokonaishintaisten-toteumien-tehtavat
SELECT
  t.id               AS toteumaid,
  t.alkanut          AS alkanut,
  t.paattynyt        AS paattynyt,
  tt.toimenpidekoodi AS toimenpidekoodi,
  tk.nimi            AS nimi,
  tt.id              AS tehtavaid,
  tt.maara           AS maara,
  tk.yksikko         AS yksikko,
  k.jarjestelma      AS jarjestelmanlisaama
FROM toteuma t
  LEFT JOIN toteuma_tehtava tt
    ON tt.toteuma = t.id AND tt.poistettu IS NOT TRUE
  LEFT JOIN toimenpidekoodi tk
    ON tk.id = tt.toimenpidekoodi
  LEFT JOIN kayttaja k
    ON k.id = t.luoja
WHERE
  t.urakka = :urakkaid
  AND t.sopimus = :sopimusid
  AND t.alkanut >= :alkanut
  AND t.paattynyt <= :paattynyt
  AND t.tyyppi = 'kokonaishintainen' :: toteumatyyppi
  AND t.poistettu IS NOT TRUE
  AND (:toimenpide :: INTEGER IS NULL OR
       tk.emo = (SELECT toimenpide
                 FROM toimenpideinstanssi
                 WHERE id = :toimenpide))
  AND (:tehtava :: INTEGER IS NULL OR tk.id = :tehtava)
ORDER BY t.alkanut
LIMIT 501;

-- name: hae-urakan-kokonaishintaiset-toteumat
SELECT t.id AS toteumaid
FROM toteuma t
  INNER JOIN toteuma_tehtava tt
    ON tt.toteuma = t.id AND tt.poistettu IS NOT TRUE
  INNER JOIN toimenpidekoodi tk
    ON tk.id = tt.toimenpidekoodi
WHERE
  t.urakka = :urakkaid
  AND t.sopimus = :sopimusid
  AND t.alkanut >= :alkanut
  AND t.paattynyt <= :paattynyt
  AND t.tyyppi = 'kokonaishintainen' :: toteumatyyppi
  AND t.poistettu IS NOT TRUE
  AND (:toimenpide :: INTEGER IS NULL OR
       tk.emo = (SELECT toimenpide
                 FROM toimenpideinstanssi
                 WHERE id = :toimenpide))
  AND (:tehtava :: INTEGER IS NULL OR tk.id = :tehtava)
ORDER BY t.alkanut
LIMIT 501;

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
  rp.toteuma = :toteuma_id;

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
  tr_numero        AS tie,
  tr_alkuosa       AS aosa,
  tr_alkuetaisyys  AS aet,
  tr_loppuosa      AS losa,
  tr_loppuetaisyys AS let,
  piiri,
  kuntoluokka,
  karttapvm,
  tr_puoli,
  tr_ajorata,
  t.alkanut        AS alkupvm,
  t.paattynyt      AS loppupvm,
  arvot,
  tierekisteriurakkakoodi,
  t.id             AS toteuma_id,
  rp.id            AS reittipiste_id,
  rp.aika          AS reittipiste_aika,
  rp.sijainti      AS reittipiste_sijainti
FROM varustetoteuma vt
  JOIN toteuma t ON vt.toteuma = t.id
  JOIN reittipiste rp ON rp.toteuma = t.id
WHERE urakka = :urakka
      AND sopimus = :sopimus
      AND alkanut >= :alkupvm
      AND alkanut <= :loppupvm
      AND (:rajaa_tienumerolla = FALSE OR tr_numero = :tienumero)
ORDER BY t.alkanut
LIMIT 501;
