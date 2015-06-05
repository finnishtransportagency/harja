-- name: listaa-urakan-toteumat
-- Listaa kaikki urakan toteumat
SELECT t.id, t.alkanut, t.paattynyt, t.tyyppi,
  (SELECT array_agg(concat(tpk.id, '^', tpk.nimi,'^', tt.maara)) FROM toteuma_tehtava tt
    LEFT JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
  WHERE tt.toteuma = t.id
        AND tt.poistettu is not true)
    as tehtavat
FROM toteuma t WHERE
    urakka = :urakka
    AND sopimus = :sopimus
    AND alkanut >= :alkupvm
    AND paattynyt <= :loppupvm
    AND t.poistettu IS NOT TRUE
GROUP BY t.id, t.alkanut, t.paattynyt, t.tyyppi;

-- name: hae-toteuman-toteuma-materiaalit-ja-tehtavat
-- Hakee toteuma_materiaalien ja tehtävien id:t. Hyödyllinen kun poistetaan toteuma.
SELECT tt.id as tehtava_id, tm.id as materiaali_id
FROM toteuma t
  LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
  LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
WHERE t.id IN (:id);

-- name: hae-urakan-toteuma-paivat
-- Hakee päivät tietyllä aikavälillä, jolle urakalla on toteumia.
SELECT DISTINCT date_trunc('day', alkanut) as paiva
  FROM toteuma
 WHERE urakka = :urakka
       AND sopimus = :sopimus
       AND alkanut >= :alkupvm
       AND paattynyt <= :loppupvm
       AND poistettu IS NOT true;

-- name: hae-urakan-tehtavat
-- Hakee tehtävät, joita annetulle urakalle voi kirjata.
SELECT id,nimi,yksikko FROM toimenpidekoodi
 WHERE taso = 4
   AND poistettu IS NOT true
   AND emo IN (SELECT toimenpide FROM toimenpideinstanssi WHERE urakka = :urakka);

-- name: hae-urakan-tehtavat-toimenpidekoodilla
-- Hakee urakan tehtävät tietyllä toimenpidekoodilla
SELECT tt.id as tehtava_id, tt.toteuma as toteuma_id, tt.toimenpidekoodi, tt.maara,
(SELECT nimi FROM toimenpidekoodi tpk WHERE id = tt.toimenpidekoodi) as toimenpide,
(SELECT tyyppi FROM toteuma t WHERE t.id = tt.toteuma) as tyyppi
FROM toteuma_tehtava tt
RIGHT JOIN toteuma t ON tt.toteuma = t.id
      AND sopimus = 1
      AND urakka = 1
      AND alkanut >= '2005-10-01 00:00.00'
      AND paattynyt <= '2006-09-30 00:00.00'
WHERE toimenpidekoodi = 1350
AND tt.poistettu IS NOT true;

-- name: paivita-toteuma!
UPDATE toteuma
SET alkanut=:alkanut, paattynyt=:paattynyt, muokattu=NOW(), muokkaaja=:kayttaja
WHERE id=:id AND urakka=:urakka;

-- name: luo-toteuma<!
-- Luo uuden toteuman.
INSERT
  INTO toteuma
       (urakka, sopimus, alkanut, paattynyt, tyyppi, luotu, luoja, poistettu)
VALUES (:urakka, :sopimus, :alkanut, :paattynyt, :tyyppi::toteumatyyppi, NOW(), :kayttaja, false);

-- name: poista-toteuma!
UPDATE toteuma
SET muokattu=NOW(), muokkaaja=:kayttaja, poistettu=true
WHERE id IN (:id) AND poistettu IS NOT true;

-- name: luo-tehtava<!
-- Luo uuden tehtävän toteumalle
INSERT
  INTO toteuma_tehtava
       (toteuma, toimenpidekoodi, maara, luotu, luoja, poistettu)
VALUES (:toteuma, :toimenpidekoodi, :maara, NOW(), :kayttaja, false);

-- name: poista-tehtava!
UPDATE toteuma_tehtava
SET muokattu=NOW(), muokkaaja=:kayttaja, poistettu=true
WHERE id IN (:id) AND poistettu IS NOT true;

-- name: listaa-urakan-tehtavat-toteumittain
-- listaa-toteuman-tehtavat ID:n avulla
SELECT t.id, t.alkanut, t.tyyppi, t.suorittajan_nimi, t.suorittajan_ytunnus, t.lisatieto, SUM(tt.maara) as maara
  FROM toteuma t JOIN toteuma_tehtava tt ON tt.toteuma=t.id
 WHERE (t.alkanut >= :alkupvm AND t.alkanut <= :loppupvm)
       AND toimenpidekoodi = :toimenpidekoodi
       AND urakka = :urakka
       AND sopimus = :sopimus
       AND t.poistettu IS NOT true
       AND tt.poistettu IS NOT true
 GROUP BY t.id;

-- name: listaa-urakan-hoitokauden-erilliskustannukset
-- Listaa urakan erilliskustannukset
SELECT id, tyyppi, sopimus, toimenpideinstanssi, pvm,
       rahasumma, indeksin_nimi, lisatieto, luotu, luoja
  FROM erilliskustannus
 WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = :urakka)
   AND pvm >= :alkupvm AND pvm <= :loppupvm AND poistettu = false;

-- name: luo-erilliskustannus<!
-- Listaa urakan erilliskustannukset
INSERT
  INTO erilliskustannus
       (tyyppi, sopimus, toimenpideinstanssi, pvm,
       rahasumma, indeksin_nimi, lisatieto, luotu, luoja)
VALUES (:tyyppi::erilliskustannustyyppi, :sopimus, :toimenpideinstanssi, :pvm,
       :rahasumma, :indeksin_nimi, :lisatieto, NOW(), :luoja);

-- name: paivita-erilliskustannus!
-- Päivitä erilliskustannus
UPDATE erilliskustannus
   SET tyyppi = :tyyppi::erilliskustannustyyppi, sopimus = :sopimus, toimenpideinstanssi = :toimenpideinstanssi, pvm = :pvm,
       rahasumma = :rahasumma, indeksin_nimi = :indeksin_nimi, lisatieto = :lisatieto, muokattu = NOW(), muokkaaja = :muokkaaja,
       poistettu = :poistettu
 WHERE id = :id;
