-- name: hae-tiedot-urakan-suolasakkoraportille
SELECT
  u.nimi AS urakka_nimi,
  ss.maara AS sakko_maara,
  ss.hoitokauden_alkuvuosi AS sakko_hoitokauden_alkuvuosi,
  ss.maksukuukausi AS sakko_maksukuukausi,
  ss.indeksi AS sakko_indeksi,
  ss.urakka,
  ss.indeksi,
  lt.alkupvm AS lampotila_alkupvm,
  lt.loppupvm AS lampotila_loppupvm,
  lt.keskilampotila as keskilampotila,
  lt.pitka_keskilampotila as pitkakeskilampotila,
  --(SELECT "hoitokauden_suolasakko"(:urakka, '2014-10-01', '2015-09-30')) AS suolasakko, -- FIXME ERROR: function hoitokauden_suolasakko(bigint, unknown, unknown) does not exist???
  (SELECT SUM(maara) AS suola_suunniteltu
   FROM materiaalin_kaytto mk
   WHERE mk.urakka = :urakka
        AND mk.materiaali IN (SELECT id FROM materiaalikoodi
   WHERE materiaalityyppi = 'talvisuola'::materiaalityyppi)
        AND mk.alkupvm = '2014-10-01' AND mk.loppupvm = '2015-09-30'),
   (SELECT SUM(maara) AS suola_kaytetty
    FROM toteuma_materiaali tm
    JOIN materiaalikoodi mk ON tm.materiaalikoodi=mk.id
    JOIN toteuma t ON tm.toteuma = t.id
    WHERE mk.materiaalityyppi = 'talvisuola'::materiaalityyppi
        AND t.urakka = 4
        AND t.alkanut >= '2014-10-01' AND t.alkanut <= '2015-09-30')
FROM lampotilat lt
  LEFT JOIN suolasakko ss ON ss.urakka = lt.urakka
                             AND ss.hoitokauden_alkuvuosi = (SELECT EXTRACT(YEAR FROM lt.alkupvm))
  LEFT JOIN urakka u ON ss.urakka = u.id
WHERE lt.urakka = :urakka
AND ss.hoitokauden_alkuvuosi = :alkuvuosi
AND lt.alkupvm >= '2014-10-01'
AND lt.alkupvm <= '2015-09-30';