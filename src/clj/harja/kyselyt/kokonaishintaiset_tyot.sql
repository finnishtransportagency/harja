-- name: listaa-urakan-kokonaishintaiset-tyot
-- Hakee kaikki kokonaishintaiset-tyot
SELECT    kt.vuosi, kt.kuukausi, kt.summa, kt.maksupvm, kt.toimenpideinstanssi, kt.sopimus,
          tpi.nimi as tpi_nimi, tpi.toimenpide as toimenpide
  FROM    kokonaishintainen_tyo kt
  	      LEFT JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
 WHERE    tpi.urakka = :urakka
 ORDER BY vuosi, kuukausi