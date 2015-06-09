-- name: listaa-urakan-toteumat
-- Listaa kaikki urakan toteumat
SELECT
  t.id,
  t.alkanut,
  t.paattynyt,
  t.tyyppi,
  (SELECT array_agg(concat(tpk.id, '^', tpk.nimi, '^', tt.maara))
   FROM toteuma_tehtava tt
     LEFT JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
   WHERE tt.toteuma = t.id
         AND tt.poistettu IS NOT TRUE)
    AS tehtavat
FROM toteuma t
WHERE
  urakka = :urakka
  AND sopimus = :sopimus
  AND alkanut >= :alkupvm
  AND paattynyt <= :loppupvm
  AND t.poistettu IS NOT TRUE
GROUP BY t.id, t.alkanut, t.paattynyt, t.tyyppi;

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

-- name: hae-urakan-toteutuneet-tehtavat
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
                          AND tyyppi = :tyyppi::toteumatyyppi

-- name: paivita-toteuma!
UPDATE toteuma
SET alkanut        = :alkanut, paattynyt = :paattynyt, muokattu = NOW(), muokkaaja = :kayttaja,
  suorittajan_nimi = :suorittajan_nimi, suorittajan_ytunnus = :ytunnus, lisatieto = :lisatieto
WHERE id = :id AND urakka = :urakka;

-- name: luo-toteuma<!
-- Luo uuden toteuman.
INSERT
INTO toteuma
(urakka, sopimus, alkanut, paattynyt, tyyppi, luotu, luoja,
 poistettu, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
VALUES (:urakka, :sopimus, :alkanut, :paattynyt, :tyyppi :: toteumatyyppi, NOW(), :kayttaja,
        FALSE, :suorittaja, :tunnus, :lisatieto);

-- name: poista-toteuma!
UPDATE toteuma
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE id IN (:id) AND poistettu IS NOT TRUE;

-- name: luo-tehtava<!
-- Luo uuden tehtävän toteumalle
INSERT
INTO toteuma_tehtava
(toteuma, toimenpidekoodi, maara, luotu, luoja, poistettu)
VALUES (:toteuma, :toimenpidekoodi, :maara, NOW(), :kayttaja, FALSE);

-- name: poista-tehtava!
UPDATE toteuma_tehtava
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE id IN (:id) AND poistettu IS NOT TRUE;

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
  luoja
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

-- name: paivita-urakan-yk-hint-toteumien-tehtavat!
-- Päivitä urakan yksikköhintaisten töiden toteumien tehtävät
UPDATE toteuma_tehtava
SET maara = :maara
WHERE id = :id;

-- name: merkitse-toteuman-maksuera-likaiseksi!
-- Merkitsee toteumaa vastaavan maksuerän likaiseksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE maksuera
SET likainen = TRUE
WHERE
  tyyppi = :tyyppi::maksueratyyppi  AND
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
             WHERE tt.id IN (:toteuma_tehtava_idt) AND t.tyyppi::TEXT = m.tyyppi :: TEXT);

-- name: merkitse-toimenpideinstanssin-kustannussuunnitelma-likaiseksi!
-- Merkitsee erilliskustannuksia vastaavan maksuerän likaiseksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE maksuera
SET likainen = TRUE
WHERE
  tyyppi = 'muu' AND
  toimenpideinstanssi IN (SELECT id
                          FROM toimenpideinstanssi
                          WHERE id = :toimenpideinstanssi);
