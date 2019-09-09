-- name:tallenna-budjettitavoite<!
INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, tavoitehinta_siirretty, kattohinta, luotu, luoja) VALUES
(:urakka, :hoitokausi, :tavoitehinta, :tavoitehinta_siirretty, :kattohinta, current_timestamp, :kayttaja);

-- name:paivita-budjettitavoite<!
UPDATE urakka_tavoite
SET tavoitehinta = :tavoitehinta,
tavoitehinta_siirretty = :tavoitehinta_siirretty,
kattohinta = :kattohinta,
muokattu = current_timestamp,
muokkaaja = :kayttaja WHERE
urakka = :urakka AND hoitokausi = :hoitokausi;

-- name:hae-budjettitavoite
SELECT * from urakka_tavoite
WHERE urakka = :urakka;

-- name: hae-summat-kokonaishintaiseen-kustannussuunnitelmaan
SELECT kt.toimenpideinstanssi as toimenpideinstanssi,
       kt.vuosi               as vuosi,
       kt.kuukausi            as kuukausi,
       kt.summa               as summa
FROM kokonaishintainen_tyo kt
WHERE kt.toimenpideinstanssi = :toimenpideinstanssi
  AND kt.vuosi = :vuosi
  AND kt.kuukausi = :kuukausi
UNION ALL
SELECT at.toimenpideinstanssi as toimenpideinstanssi,
       at.vuosi               as vuosi,
       at.kuukausi            as kuukausi,
       at.summa               as summa
FROM kustannusarvioitu_tyo at
WHERE at.toimenpideinstanssi = :toimenpideinstanssi
  AND at.vuosi = :vuosi
  AND at.kuukausi = :kuukausi
UNION ALL
SELECT yt.toimenpideinstanssi as toimenpideinstanssi,
       yt.vuosi               as vuosi,
       yt.kuukausi            as kuukausi,
       yt.arvioitu_kustannus  as summa
FROM yksikkohintainen_tyo yt
WHERE yt.toimenpideinstanssi = :toimenpideinstanssi
  AND yt.vuosi = :vuosi
  AND yt.kuukausi = :kuukausi;

