-- name: hae-tiedot-urakan-suolasakkoraportille
SELECT
  u.nimi AS urakka_nimi,
  ss.maara AS sakko_maara,
  ss.hoitokauden_alkuvuosi AS sakko_hoitokauden_alkuvuosi,
  ss.maksukuukausi AS sakko_maksukuukausi,
  ss.indeksi AS sakko_indeksi,
  ss.urakka,
  lt.alkupvm AS lampotila_alkupvm,
  lt.loppupvm AS lampotila_loppupvm,
  lt.keskilampotila as keskilampotila,
  lt.pitka_keskilampotila as pitkakeskilampotila,
  (SELECT "hoitokauden_suolasakko"(4, '2014-10-01', '2015-09-30')) AS suolasakko
FROM lampotilat lt
  LEFT JOIN suolasakko ss ON ss.urakka = lt.urakka
                             AND ss.hoitokauden_alkuvuosi = (SELECT EXTRACT(YEAR FROM lt.alkupvm))
  LEFT JOIN urakka u ON ss.urakka = u.id
WHERE lt.urakka = :urakka
AND ss.hoitokauden_alkuvuosi = :alkuvuosi
AND lt.alkupvm :: DATE >= :alkupvm
AND lt.alkupvm :: DATE <= :loppupvm;
-- FIXME Pitäisi rajata tarkasti alku = alku ja loppu = loppu, jotta tulos varmasti pyydetyltä aikaväliltä.
-- Mutta jostain syystä ei toiminut niin. Jotain tekemistä timezonen kanssa?