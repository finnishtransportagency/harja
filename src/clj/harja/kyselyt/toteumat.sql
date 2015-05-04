-- name: listaa-urakan-toteumat
-- Listaa kaikki urakan toteumat
SELECT t.id, t.alkanut, t.paattynyt, t.tyyppi,
       (SELECT array_agg(tpk.nimi) FROM toimenpidekoodi tpk
         WHERE tpk.id IN (SELECT toimenpidekoodi FROM toteuma_tehtava tt WHERE tt.toteuma=t.id)) as tehtavat,
       (SELECT array_agg(mk.nimi) FROM materiaalikoodi mk
         WHERE mk.id in (SELECT materiaalikoodi FROM toteuma_materiaali tm WHERE tm.toteuma=t.id)) as materiaalit
  FROM toteuma t
 WHERE urakka = :urakka AND sopimus = :sopimus
   AND alkanut >= :alkupvm AND paattynyt <= :loppupvm
GROUP BY t.id, t.alkanut, t.paattynyt, t.tyyppi;


-- name: hae-urakan-toteuma-paivat
-- Hakee päivät tietyllä aikavälillä, jolle urakalla on toteumia.
SELECT DISTINCT date_trunc('day', alkanut) as paiva
  FROM toteuma
 WHERE urakka = :urakka AND sopimus = :sopimus
   AND alkanut >= :alkupvm AND paattynyt <= :loppupvm;

-- name: hae-urakan-tehtavat
-- Hakee tehtävät, joita annetulle urakalle voi kirjata.
SELECT id,nimi,yksikko FROM toimenpidekoodi
 WHERE taso = 4
   AND poistettu = false
   AND emo IN (SELECT toimenpide FROM toimenpideinstanssi WHERE urakka = :urakka);
