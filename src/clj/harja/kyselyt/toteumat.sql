-- name: listaa-urakan-toteumat
-- Listaa kaikki urakan toteumat
SELECT t.id, t.alkanut, t.paattynyt, t.tyyppi,
  (SELECT array_agg(concat(tpk.id, '^', tpk.nimi,'^', tt.maara)) FROM toteuma_tehtava tt
    LEFT JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
  WHERE tt.toteuma = t.id) as tehtavat FROM toteuma t WHERE
    urakka = :urakka
    AND sopimus = :sopimus
    AND alkanut >= :alkupvm
    AND paattynyt <= :loppupvm
GROUP BY t.id, t.alkanut, t.paattynyt, t.tyyppi;


-- name: hae-urakan-toteuma-paivat
-- Hakee päivät tietyllä aikavälillä, jolle urakalla on toteumia.
SELECT DISTINCT date_trunc('day', alkanut) as paiva
  FROM toteuma
 WHERE urakka = :urakka AND sopimus = :sopimus
   AND alkanut >= :alkupvm AND paattynyt <= :loppupvm

-- name: hae-urakan-tehtavat
-- Hakee tehtävät, joita annetulle urakalle voi kirjata.
SELECT id,nimi,yksikko FROM toimenpidekoodi
 WHERE taso = 4
   AND poistettu = false
   AND emo IN (SELECT toimenpide FROM toimenpideinstanssi WHERE urakka = :urakka);

-- name: paivita-toteuma!
UPDATE toteuma
SET alkanut=:alkanut, paattynyt=:paattynyt
WHERE id=:id AND urakka=:urakka;

-- name: luo-toteuma<!
-- Luo uuden toteuman.
INSERT
  INTO toteuma
       (urakka, sopimus, alkanut, paattynyt, tyyppi, luotu)
VALUES (:urakka, :sopimus, :alkanut, :paattynyt, :tyyppi::toteumatyyppi, NOW());

-- name: luo-tehtava<!
-- Luo uuden tehtävän toteumalle
INSERT
  INTO toteuma_tehtava
       (toteuma, toimenpidekoodi, maara, luotu)
VALUES (:toteuma, :toimenpidekoodi, :maara, NOW());

-- name: listaa-urakan-tehtavat-toteumittain
-- listaa-toteuman-tehtavat ID:n avulla
SELECT t.id, t.alkanut, t.tyyppi, t.suorittajan_nimi, t.suorittajan_ytunnus, t.lisatieto, SUM(tt.maara) as maara
  FROM toteuma t JOIN toteuma_tehtava tt ON tt.toteuma=t.id
 WHERE (t.alkanut >= :alkupvm AND t.alkanut <= :loppupvm)
       AND toimenpidekoodi = :toimenpidekoodi
       AND urakka = :urakka
       AND sopimus = :sopimus
 GROUP BY t.id

-- name: listaa-urakan-hoitokauden-erilliskustannukset
-- Listaa urakan erilliskustannukset
SELECT id, tyyppi, sopimus, toimenpideinstanssi, pvm,
       rahasumma, indeksin_nimi, lisatieto, luotu, luoja
  FROM erilliskustannus
 WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = :urakka)
   AND pvm >= :alkupvm AND pvm <= :loppupvm AND poistettu = false

-- name: luo-erilliskustannus<!
-- Listaa urakan erilliskustannukset
INSERT
  INTO erilliskustannus
       (tyyppi, sopimus, toimenpideinstanssi, pvm,
       rahasumma, indeksin_nimi, lisatieto, luotu, luoja)
VALUES (:tyyppi::erilliskustannustyyppi, :sopimus, :toimenpideinstanssi, :pvm,
       :rahasumma, :indeksin_nimi, :lisatieto, NOW(), :luoja)

-- name: paivita-erilliskustannus!
-- Päivitä erilliskustannus
UPDATE erilliskustannus
   SET tyyppi = :tyyppi::erilliskustannustyyppi, sopimus = :sopimus, toimenpideinstanssi = :toimenpideinstanssi, pvm = :pvm,
       rahasumma = :rahasumma, indeksin_nimi = :indeksin_nimi, lisatieto = :lisatieto, muokattu = NOW(), muokkaaja = :muokkaaja,
       poistettu = :poistettu
 WHERE id = :id
