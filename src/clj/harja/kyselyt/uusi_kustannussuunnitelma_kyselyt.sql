-- name: hae-urakan-toimenpiteet
SELECT t.id, t.nimi, t.koodi, tpi.id as "toimenpideinstanssi-id"
FROM toimenpideinstanssi tpi
         JOIN toimenpide t ON tpi.toimenpide = t.id
WHERE tpi.urakka = :urakkaid;

-- name: hae-kiinteat-kustannukset-kuukausittain
SELECT id, vuosi, kuukausi, summa, kiinteahintainen_tyo.summa_indeksikorjattu, toimenpideinstanssi,
       tehtavaryhma, tehtava, sopimus
  FROM kiinteahintainen_tyo
 WHERE sopimus = :sopimus-id
   AND vuosi = :vuosi
   and toimenpideinstanssi = :toimenpideinstanssi-id
   AND kuukausi in (:kuukaudet);
