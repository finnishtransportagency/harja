-- name: hae-laskutusyhteenvedon-tiedot
-- Hakee laskutusyhteenvetoon tarvittavat tiedot

SELECT
  tpk2.nimi,
  (SELECT SUM(summa)
   FROM kokonaishintainen_tyo
   WHERE toimenpideinstanssi = tpi.id
         AND maksupvm >= :hk_alkupvm
         AND maksupvm <= :hk_loppupvm
         AND maksupvm < :aikavali_alkupvm)
    AS kht_laskutettu_hoitokaudella_ennen_aikavalia,
  (SELECT SUM(summa)
   FROM kokonaishintainen_tyo
   WHERE toimenpideinstanssi = tpi.id
         AND maksupvm >= :hk_alkupvm
         AND maksupvm <= :hk_loppupvm
         AND maksupvm >= :aikavali_alkupvm
         AND maksupvm <= :aikavali_loppupvm)
    AS kht_laskutetaan_aikavalilla,

  (SELECT SUM(tt.maara * yht.yksikkohinta)
   FROM toteuma_tehtava tt
     JOIN toteuma t ON tt.toteuma = t.id
     JOIN yksikkohintainen_tyo yht ON (tt.toimenpidekoodi = yht.tehtava
                                       AND yht.alkupvm <= t.alkanut AND yht.loppupvm >= t.paattynyt
                                       AND tpk3.id = (SELECT emo
                                                      FROM toimenpidekoodi tt_tpk
                                                      WHERE tt_tpk.id = tt.toimenpidekoodi))
   WHERE yht.urakka = :urakka
         AND t.urakka = :urakka
         AND t.alkanut >= :hk_alkupvm AND t.alkanut <= :hk_loppupvm
         AND t.alkanut <= :aikavali_alkupvm AND t.paattynyt <= :aikavali_alkupvm)
    AS yht_laskutettu_hoitokaudella_ennen_aikavalia,

  (SELECT SUM(tt.maara * yht.yksikkohinta)
   FROM toteuma_tehtava tt
     JOIN toteuma t ON tt.toteuma = t.id
     JOIN yksikkohintainen_tyo yht ON (tt.toimenpidekoodi = yht.tehtava
                                       AND yht.alkupvm <= t.alkanut AND yht.loppupvm >= t.paattynyt
                                       AND tpk3.id = (SELECT emo
                                                      FROM toimenpidekoodi tt_tpk
                                                      WHERE tt_tpk.id = tt.toimenpidekoodi))
   WHERE yht.urakka = :urakka
         AND t.urakka = :urakka
         AND t.alkanut >= :hk_alkupvm AND t.alkanut <= :hk_loppupvm
         AND t.alkanut >= :aikavali_alkupvm AND t.alkanut <= :aikavali_loppupvm
         AND t.paattynyt >= :aikavali_alkupvm AND t.paattynyt <= :aikavali_loppupvm)
    AS yht_laskutetaan_aikavalilla

FROM toimenpideinstanssi tpi
  JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
  JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id
WHERE tpi.urakka = :urakka;