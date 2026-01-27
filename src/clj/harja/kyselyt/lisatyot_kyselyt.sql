-- name: hae-urakan-lisatyot
SELECT rivi, kulu, k.urakka, summa, toimenpideinstanssi, maksueratyyppi, erapaiva, lisatyon_lisatieto, tp.nimi AS toimenpide
FROM kulu_kohdistus kk JOIN kulu k ON kk.kulu  = k.id JOIN toimenpide tp ON tp.id = kk.toimenpideinstanssi
WHERE maksueratyyppi = 'lisatyo' AND k.urakka = :urakka AND erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE ORDER BY erapaiva;
