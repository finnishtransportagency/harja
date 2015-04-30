-- name: listaa-urakan-toteumat
-- Listaa kaikki urakan toteumat
SELECT t.id, t.alkanut, t.paattynyt, t.tyyppi, array_agg(tpk.nimi) as tehtavat
  FROM toteuma t
       LEFT JOIN toteuma_tehtava teht ON teht.toteuma=t.id
       LEFT JOIN toimenpidekoodi tpk ON teht.toimenpidekoodi=tpk.id
 WHERE urakka = :urakka AND sopimus = :sopimus
   AND alkanut >= :alkupvm AND paattynyt <= :loppupvm
GROUP BY t.id, t.alkanut, t.paattynyt, t.tyyppi;


-- name: hae-urakan-toteuma-paivat
-- Hakee päivät tietyllä aikavälillä, jolle urakalla on toteumia.
SELECT DISTINCT date_trunc('day', alkanut) as paiva
  FROM toteuma
 WHERE urakka = :urakka AND sopimus = :sopimus
   AND alkanut >= :alkupvm AND paattynyt <= :loppupvm
