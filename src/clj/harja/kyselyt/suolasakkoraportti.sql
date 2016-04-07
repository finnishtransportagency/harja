-- name: hae-tiedot-urakan-suolasakkoraportille
SELECT *, (suola_kaytetty - kohtuullistarkistettu_sakkoraja) as ylitys
FROM (SELECT
        u.nimi AS urakka_nimi,
        ss.hoitokauden_alkuvuosi AS sakko_hoitokauden_alkuvuosi,
        ss.maksukuukausi AS sakko_maksukuukausi,
        ss.indeksi AS sakko_indeksi,
        ss.maara AS sakko_maara_per_tonni,
        ss.talvisuolaraja as sakko_talvisuolaraja,
        lt.urakka,
        ss.indeksi,
        lt.alkupvm AS lampotila_alkupvm,
        lt.loppupvm AS lampotila_loppupvm,
        lt.keskilampotila AS keskilampotila,
        lt.pitka_keskilampotila AS pitkakeskilampotila,
        (CASE WHEN (lt.keskilampotila - lt.pitka_keskilampotila) >= 4.0 THEN 1.30 * 1.05 * ss.talvisuolaraja
         WHEN (lt.keskilampotila - lt.pitka_keskilampotila) >= 3.0 THEN 1.20 * 1.05 * ss.talvisuolaraja
         WHEN (lt.keskilampotila - lt.pitka_keskilampotila) >= 2.0 THEN 1.10 * 1.05 * ss.talvisuolaraja
         ELSE 1.05 * ss.talvisuolaraja
         END) as kohtuullistarkistettu_sakkoraja,
        (SELECT SUM(maara)
         FROM materiaalin_kaytto mk
         WHERE mk.urakka = :urakka
               AND mk.materiaali IN (SELECT id FROM materiaalikoodi
         WHERE materiaalityyppi = 'talvisuola'::materiaalityyppi)
               AND mk.alkupvm >= :alkupvm
               AND mk.alkupvm <= :loppupvm) AS suola_suunniteltu,
        (SELECT AVG(arvo/100)
         FROM indeksi
         WHERE nimi = ss.indeksi
               AND ((vuosi = :alkuvuosi AND kuukausi = 10) OR
                    (vuosi = :alkuvuosi AND kuukausi = 11) OR
                    (vuosi = :alkuvuosi AND kuukausi = 12) OR
                    (vuosi = :loppuvuosi AND kuukausi = 1) OR
                    (vuosi = :loppuvuosi AND kuukausi = 2) OR
                    (vuosi = :loppuvuosi AND kuukausi = 3))) AS kerroin,
        (SELECT SUM(maara)
         FROM toteuma_materiaali tm
           JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
           JOIN toteuma t ON tm.toteuma = t.id
         WHERE mk.materiaalityyppi = 'talvisuola'::materiaalityyppi
               AND t.urakka = :urakka
               AND t.alkanut >= :alkupvm
               AND t.alkanut <= :loppupvm) AS suola_kaytetty
      FROM lampotilat lt
        LEFT JOIN suolasakko ss ON ss.urakka = lt.urakka
                                   AND ss.hoitokauden_alkuvuosi = (SELECT EXTRACT(YEAR FROM lt.alkupvm))
        LEFT JOIN urakka u ON lt.urakka = u.id
      WHERE lt.urakka = :urakka
            AND ss.hoitokauden_alkuvuosi = :alkuvuosi
            AND (SELECT EXTRACT(YEAR FROM lt.alkupvm)) = :alkuvuosi
            AND (SELECT EXTRACT(YEAR FROM lt.loppupvm)) = :loppuvuosi) AS raportti;

-- name: hae-tiedot-hallintayksikon-suolasakkoraportille
SELECT *, (suola_kaytetty - kohtuullistarkistettu_sakkoraja) as ylitys
FROM (SELECT
        u.nimi AS urakka_nimi,
        ss.talvisuolaraja as sakko_talvisuolaraja,
        ss.hoitokauden_alkuvuosi AS sakko_hoitokauden_alkuvuosi,
        ss.maksukuukausi AS sakko_maksukuukausi,
        ss.indeksi AS sakko_indeksi,
        ss.maara AS sakko_maara_per_tonni,
        lt.urakka,
        ss.indeksi,
        lt.alkupvm AS lampotila_alkupvm,
        lt.loppupvm AS lampotila_loppupvm,
        lt.keskilampotila as keskilampotila,
        lt.pitka_keskilampotila as pitkakeskilampotila,
        (CASE WHEN (lt.keskilampotila - lt.pitka_keskilampotila) >= 4.0 THEN 1.30 * 1.05 * ss.talvisuolaraja
         WHEN (lt.keskilampotila - lt.pitka_keskilampotila) >= 3.0 THEN 1.20 * 1.05 * ss.talvisuolaraja
         WHEN (lt.keskilampotila - lt.pitka_keskilampotila) >= 2.0 THEN 1.10 * 1.05 * ss.talvisuolaraja
         ELSE 1.05 * ss.talvisuolaraja
         END) as kohtuullistarkistettu_sakkoraja,
        (SELECT SUM(maara)
         FROM materiaalin_kaytto mk
         WHERE mk.urakka IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko
                             AND (:urakkatyyppi IS NULL OR tyyppi = :urakkatyyppi::urakkatyyppi))
               AND mk.materiaali IN (SELECT id FROM materiaalikoodi
         WHERE materiaalityyppi = 'talvisuola'::materiaalityyppi)
               AND mk.alkupvm >= :alkupvm
               AND mk.alkupvm <= :loppupvm) AS suola_suunniteltu,
        (SELECT AVG(arvo/100)
         FROM indeksi
         WHERE nimi = ss.indeksi
               AND ((vuosi = :alkuvuosi AND kuukausi = 10) OR
                    (vuosi = :alkuvuosi AND kuukausi = 11) OR
                    (vuosi = :alkuvuosi AND kuukausi = 12) OR
                    (vuosi = :loppuvuosi AND kuukausi = 1) OR
                    (vuosi = :loppuvuosi AND kuukausi = 2) OR
                    (vuosi = :loppuvuosi AND kuukausi = 3))) AS kerroin,
        (SELECT SUM(maara)
         FROM toteuma_materiaali tm
           JOIN materiaalikoodi mk ON tm.materiaalikoodi=mk.id
           JOIN toteuma t ON tm.toteuma = t.id
         WHERE mk.materiaalityyppi = 'talvisuola'::materiaalityyppi
               AND t.urakka IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko
                                AND (:urakkatyyppi IS NULL OR tyyppi = :urakkatyyppi::urakkatyyppi))
               AND t.alkanut >= :alkupvm
               AND t.alkanut <= :loppupvm) AS suola_kaytetty
      FROM lampotilat lt
        LEFT JOIN suolasakko ss ON ss.urakka = lt.urakka
                                   AND ss.hoitokauden_alkuvuosi = (SELECT EXTRACT(YEAR FROM lt.alkupvm))
        LEFT JOIN urakka u ON lt.urakka = u.id
      WHERE lt.urakka IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko
                          AND (:urakkatyyppi IS NULL OR tyyppi = :urakkatyyppi::urakkatyyppi))
            AND ss.hoitokauden_alkuvuosi = :alkuvuosi
            AND (SELECT EXTRACT(YEAR FROM lt.alkupvm)) = :alkuvuosi
            AND (SELECT EXTRACT(YEAR FROM lt.loppupvm)) = :loppuvuosi) AS raportti;

-- name: hae-tiedot-koko-maan-suolasakkoraportille
SELECT *, (suola_kaytetty - kohtuullistarkistettu_sakkoraja) as ylitys
FROM (SELECT
        u.nimi AS urakka_nimi,
        ss.talvisuolaraja as sakko_talvisuolaraja,
        ss.hoitokauden_alkuvuosi AS sakko_hoitokauden_alkuvuosi,
        ss.maksukuukausi AS sakko_maksukuukausi,
        ss.indeksi AS sakko_indeksi,
        ss.maara AS sakko_maara_per_tonni,
        lt.urakka,
        ss.indeksi,
        lt.alkupvm AS lampotila_alkupvm,
        lt.loppupvm AS lampotila_loppupvm,
        lt.keskilampotila as keskilampotila,
        lt.pitka_keskilampotila as pitkakeskilampotila,
        (CASE WHEN (lt.keskilampotila - lt.pitka_keskilampotila) >= 4.0 THEN 1.30 * 1.05 * ss.talvisuolaraja
         WHEN (lt.keskilampotila - lt.pitka_keskilampotila) >= 3.0 THEN 1.20 * 1.05 * ss.talvisuolaraja
         WHEN (lt.keskilampotila - lt.pitka_keskilampotila) >= 2.0 THEN 1.10 * 1.05 * ss.talvisuolaraja
         ELSE 1.05 * ss.talvisuolaraja
         END) as kohtuullistarkistettu_sakkoraja,
        (SELECT SUM(maara)
         FROM materiaalin_kaytto mk
         WHERE mk.urakka IN (SELECT id FROM urakka WHERE (:urakkatyyppi IS NULL OR tyyppi = :urakkatyyppi::urakkatyyppi))
               AND mk.materiaali IN (SELECT id FROM materiaalikoodi
         WHERE materiaalityyppi = 'talvisuola'::materiaalityyppi)
               AND mk.alkupvm >= :alkupvm
               AND mk.alkupvm <= :loppupvm) AS suola_suunniteltu,
        (SELECT AVG(arvo/100)
         FROM indeksi
         WHERE nimi = ss.indeksi
               AND ((vuosi = :alkuvuosi AND kuukausi = 10) OR
                    (vuosi = :alkuvuosi AND kuukausi = 11) OR
                    (vuosi = :alkuvuosi AND kuukausi = 12) OR
                    (vuosi = :loppuvuosi AND kuukausi = 1) OR
                    (vuosi = :loppuvuosi AND kuukausi = 2) OR
                    (vuosi = :loppuvuosi AND kuukausi = 3))) AS kerroin,
        (SELECT SUM(maara)
         FROM toteuma_materiaali tm
           JOIN materiaalikoodi mk ON tm.materiaalikoodi=mk.id
           JOIN toteuma t ON tm.toteuma = t.id
         WHERE mk.materiaalityyppi = 'talvisuola'::materiaalityyppi
               AND t.urakka IN (SELECT id FROM urakka WHERE (:urakkatyyppi IS NULL OR tyyppi = :urakkatyyppi::urakkatyyppi))
               AND t.alkanut >= :alkupvm
               AND t.alkanut <= :loppupvm) AS suola_kaytetty
      FROM lampotilat lt
        LEFT JOIN suolasakko ss ON ss.urakka = lt.urakka
                                   AND ss.hoitokauden_alkuvuosi = (SELECT EXTRACT(YEAR FROM lt.alkupvm))
        LEFT JOIN urakka u ON lt.urakka = u.id
      WHERE lt.urakka IN (SELECT id FROM urakka WHERE (:urakkatyyppi IS NULL OR tyyppi = :urakkatyyppi::urakkatyyppi))
            AND ss.hoitokauden_alkuvuosi = :alkuvuosi
            AND (SELECT EXTRACT(YEAR FROM lt.alkupvm)) = :alkuvuosi
            AND (SELECT EXTRACT(YEAR FROM lt.loppupvm)) = :loppuvuosi) AS raportti;