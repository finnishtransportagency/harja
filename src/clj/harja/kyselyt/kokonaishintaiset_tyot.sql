-- name: listaa-kokonaishintaiset-tyot
-- Hakee kaikki urakan kokonaishintaiset-tyot
SELECT    kt.vuosi, kt.kuukausi, kt.summa, kt.maksupvm, kt.toimenpideinstanssi, kt.sopimus,
          tpi.nimi as tpi_nimi, tpi.toimenpide as toimenpide
  FROM    kokonaishintainen_tyo kt
  	      LEFT JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
 WHERE    tpi.urakka = :urakka
 ORDER BY vuosi, kuukausi

-- name: paivita-kokonaishintainen-tyo!
-- Päivittää kokonaishintaisen tyon summan ja maksupvm:n, tunnisteena tpi, sop, vu ja kk

UPDATE kokonaishintainen_tyo
   SET summa = :summa, maksupvm = :maksupvm
 WHERE toimenpideinstanssi = :toimenpideinstanssi AND sopimus = :sopimus
       AND vuosi = :vuosi AND kuukausi = :kuukausi


-- name: lisaa-kokonaishintainen-tyo<!
-- Lisää kokonaishintaisen tyon
INSERT INTO kokonaishintainen_tyo
            (summa, maksupvm, toimenpideinstanssi, sopimus, vuosi, kuukausi)
	 VALUES (:summa, :maksupvm, :toimenpideinstanssi, :sopimus, :vuosi, :kuukausi)