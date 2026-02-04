-- name: hae-urakan-lisatyot
SELECT kk.rivi,
       kk.kulu,
       k.urakka,
       kk.summa,
       kk.toimenpideinstanssi,
       kk.maksueratyyppi,
       k.erapaiva,
       kk.lisatyon_lisatieto,
       tp.nimi AS toimenpide
  FROM kulu_kohdistus kk
           JOIN kulu k ON kk.kulu = k.id AND k.poistettu IS NOT TRUE
           JOIN toimenpideinstanssi tp ON tp.id = kk.toimenpideinstanssi
 WHERE kk.maksueratyyppi = 'lisatyo'
   AND k.urakka = :urakka
   AND k.erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE
 ORDER BY erapaiva;
