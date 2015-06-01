-- name: listaa-urakan-toteumat
-- Listaa kaikki urakan toteumat
SELECT t.id, t.alkanut, t.paattynyt, t.tyyppi,
       (SELECT array_agg(concat(tpk.nimi, '^', tt.toimenpidekoodi, '^', tt.maara)) FROM toimenpidekoodi tpk
        LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
         WHERE tpk.id IN (SELECT toimenpidekoodi FROM toteuma_tehtava tt WHERE tt.toteuma=t.id)) as tehtavat
  FROM toteuma t
 WHERE urakka = :urakka AND sopimus = :sopimus
   AND alkanut >= :alkupvm AND paattynyt <= :loppupvm
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

-- name: luo-toteuma<!
-- Luo uuden toteuman.
INSERT
  INTO toteuma
       (urakka, sopimus, alkanut, paattynyt, tyyppi, luotu)
VALUES (:urakka, :sopimus, :alkanut, :paattynyt, :tyyppi::toteumatyyppi, NOW())

-- name: luo-tehtava<!
-- Luo uuden tehtävän toteumalle
INSERT
  INTO toteuma_tehtava
       (toteuma, toimenpidekoodi, maara, luotu)
VALUES (:toteuma, :toimenpidekoodi, :maara, NOW())

-- name: luo-materiaali<!
-- Luo uuden materiaalin toteumalle
INSERT
  INTO toteuma_materiaali
       (toteuma, materiaalikoodi, maara, luotu)
VALUES (:toteuma, :materiaalikoodi, :maara, NOW())
