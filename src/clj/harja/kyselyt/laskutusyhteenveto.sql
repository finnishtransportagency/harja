-- name: hae-laskutusyhteenvedon-tiedot
-- Hakee laskutusyhteenvetoon tarvittavat tiedot

SELECT
tpk2.nimi,
  (SELECT SUM(summa) FROM kokonaishintainen_tyo
    WHERE toimenpideinstanssi = tpi.id
      AND maksupvm >= :hk_alkupvm
      AND maksupvm <= :hk_loppupvm
      AND maksupvm < :aikavali_alkupvm)
     AS kht_laskutettu_hoitokaudella_ennen_aikavalia,
  (SELECT SUM(summa) FROM kokonaishintainen_tyo
    WHERE toimenpideinstanssi=tpi.id
          AND maksupvm >= :hk_alkupvm
          AND maksupvm <= :hk_loppupvm
          AND maksupvm >= :aikavali_alkupvm
          AND maksupvm <= :aikavali_loppupvm)
       AS kht_laskutetaan_aikavalilla
  --kht_laskutettu_hoitokaudella_ennen_aikavalia + kht_laskutetaan_aikavalilla AS kht_yhteensa

FROM toimenpideinstanssi tpi
     JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
     JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id
WHERE tpi.urakka = :urakka;


--AND EXTRACT(YEAR FROM maksupvm) = :vuosi
--AND EXTRACT(MONTH FROM maksupvm) = :kuukausi
--) AS