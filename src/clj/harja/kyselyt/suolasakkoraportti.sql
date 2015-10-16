-- name: hae-tiedot-urakan-suolasakkoraportille
SELECT
  u.nimi AS urakka_nimi,
  ss.maara AS sakko_maara,
  ss.hoitokauden_alkuvuosi AS sakko_hoitokauden_alkuvuosi,
  ss.maksukuukausi AS sakko_maksukuukausi,
  ss.indeksi AS sakko_indeksi,
  lt.urakka,
  ss.indeksi,
  lt.alkupvm AS lampotila_alkupvm,
  lt.loppupvm AS lampotila_loppupvm,
  lt.keskilampotila as keskilampotila,
  lt.pitka_keskilampotila as pitkakeskilampotila,
  -- (SELECT "hoitokauden_suolasakko"(:urakka, '2014-10-01', '2015-09-30')) AS suolasakko, -- FIXME wtf function hoitokauden_suolasakko(bigint, unknown, unknown) does not exist???
  (SELECT SUM(maara) AS suola_suunniteltu
   FROM materiaalin_kaytto mk
   WHERE mk.urakka = :urakka
        AND mk.materiaali IN (SELECT id FROM materiaalikoodi
   WHERE materiaalityyppi = 'talvisuola'::materiaalityyppi)
        AND mk.alkupvm >= :alkupvm
        AND mk.alkupvm <= :loppupvm),
   (SELECT SUM(maara) AS suola_kaytetty
    FROM toteuma_materiaali tm
    JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
    JOIN toteuma t ON tm.toteuma = t.id
    WHERE mk.materiaalityyppi = 'talvisuola'::materiaalityyppi
        AND t.urakka = :urakka
        AND t.alkanut >= :alkupvm
        AND t.alkanut <= :loppupvm)
FROM lampotilat lt
  LEFT JOIN suolasakko ss ON ss.urakka = lt.urakka
                             AND ss.hoitokauden_alkuvuosi = (SELECT EXTRACT(YEAR FROM lt.alkupvm))
  LEFT JOIN urakka u ON lt.urakka = u.id
WHERE lt.urakka = :urakka
AND ss.hoitokauden_alkuvuosi = :alkuvuosi
AND (SELECT EXTRACT(YEAR FROM lt.alkupvm)) = :alkuvuosi
AND (SELECT EXTRACT(YEAR FROM lt.loppupvm)) = :loppuvuosi;

-- name: hae-tiedot-hallintayksikon-suolasakkoraportille
SELECT
  u.nimi AS urakka_nimi,
  ss.maara AS sakko_maara,
  ss.hoitokauden_alkuvuosi AS sakko_hoitokauden_alkuvuosi,
  ss.maksukuukausi AS sakko_maksukuukausi,
  ss.indeksi AS sakko_indeksi,
  lt.urakka,
  ss.indeksi,
  lt.alkupvm AS lampotila_alkupvm,
  lt.loppupvm AS lampotila_loppupvm,
  lt.keskilampotila as keskilampotila,
  lt.pitka_keskilampotila as pitkakeskilampotila,
  -- (SELECT "hoitokauden_suolasakko"(:urakka, '2014-10-01', '2015-09-30')) AS suolasakko, -- FIXME wtf function hoitokauden_suolasakko(bigint, unknown, unknown) does not exist???
  (SELECT SUM(maara) AS suola_suunniteltu
   FROM materiaalin_kaytto mk
   WHERE mk.urakka IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko)
        AND mk.materiaali IN (SELECT id FROM materiaalikoodi
   WHERE materiaalityyppi = 'talvisuola'::materiaalityyppi)
        AND mk.alkupvm >= :alkupvm
        AND mk.alkupvm <= :loppupvm),
   (SELECT SUM(maara) AS suola_kaytetty
    FROM toteuma_materiaali tm
    JOIN materiaalikoodi mk ON tm.materiaalikoodi=mk.id
    JOIN toteuma t ON tm.toteuma = t.id
    WHERE mk.materiaalityyppi = 'talvisuola'::materiaalityyppi
        AND t.urakka IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko)
        AND t.alkanut >= :alkupvm
        AND t.alkanut <= :loppupvm)
FROM lampotilat lt
  LEFT JOIN suolasakko ss ON ss.urakka = lt.urakka
                             AND ss.hoitokauden_alkuvuosi = (SELECT EXTRACT(YEAR FROM lt.alkupvm))
  LEFT JOIN urakka u ON lt.urakka = u.id
WHERE lt.urakka IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko)
AND ss.hoitokauden_alkuvuosi = :alkuvuosi
AND (SELECT EXTRACT(YEAR FROM lt.alkupvm)) = :alkuvuosi
AND (SELECT EXTRACT(YEAR FROM lt.loppupvm)) = :loppuvuosi;

-- name: hae-tiedot-koko-maan-suolasakkoraportille
SELECT
  u.nimi AS urakka_nimi,
  ss.maara AS sakko_maara,
  ss.hoitokauden_alkuvuosi AS sakko_hoitokauden_alkuvuosi,
  ss.maksukuukausi AS sakko_maksukuukausi,
  ss.indeksi AS sakko_indeksi,
  lt.urakka,
  ss.indeksi,
  lt.alkupvm AS lampotila_alkupvm,
  lt.loppupvm AS lampotila_loppupvm,
  lt.keskilampotila as keskilampotila,
  lt.pitka_keskilampotila as pitkakeskilampotila,
  -- (SELECT "hoitokauden_suolasakko"(:urakka, '2014-10-01', '2015-09-30')) AS suolasakko, -- FIXME wtf function hoitokauden_suolasakko(bigint, unknown, unknown) does not exist???
  (SELECT SUM(maara) AS suola_suunniteltu
   FROM materiaalin_kaytto mk
   WHERE mk.materiaali IN (SELECT id FROM materiaalikoodi
   WHERE materiaalityyppi = 'talvisuola'::materiaalityyppi)
        AND mk.alkupvm >= :alkupvm
        AND mk.alkupvm <= :loppupvm),
   (SELECT SUM(maara) AS suola_kaytetty
    FROM toteuma_materiaali tm
    JOIN materiaalikoodi mk ON tm.materiaalikoodi=mk.id
    JOIN toteuma t ON tm.toteuma = t.id
    WHERE mk.materiaalityyppi = 'talvisuola'::materiaalityyppi
        AND t.alkanut >= :alkupvm
        AND t.alkanut <= :loppupvm)
FROM lampotilat lt
  LEFT JOIN suolasakko ss ON ss.urakka = lt.urakka
                             AND ss.hoitokauden_alkuvuosi = (SELECT EXTRACT(YEAR FROM lt.alkupvm))
  LEFT JOIN urakka u ON lt.urakka = u.id
WHERE ss.hoitokauden_alkuvuosi = :alkuvuosi
AND (SELECT EXTRACT(YEAR FROM lt.alkupvm)) = :alkuvuosi
AND (SELECT EXTRACT(YEAR FROM lt.loppupvm)) = :loppuvuosi;