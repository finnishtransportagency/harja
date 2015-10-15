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
  lt.pitka_keskilampotila as pitkakeskilampotila
FROM lampotilat lt
  LEFT JOIN suolasakko ss ON ss.urakka = lt.urakka
                             AND ss.hoitokauden_alkuvuosi = (SELECT EXTRACT(YEAR FROM lt.alkupvm))
  LEFT JOIN urakka u ON ss.urakka = u.id
WHERE lt.urakka = :urakka
--AND ss.hoitokauden_alkuvuosi = :alkuvuosi
--AND lt.alkupvm = :alkupvm
--AND lt.loppupvm = :loppupvm;