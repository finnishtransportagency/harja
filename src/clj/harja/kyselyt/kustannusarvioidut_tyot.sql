-- name: hae-kustannusarvioidut-tyot
-- Hakee kaikki urakan kustannusarvioidut tyot
SELECT kat.id,
       kat.vuosi,
       kat.kuukausi,
       kat.summa,
       kat.tyyppi ::TOTEUMATYYPPI,
       kat.tehtava,
       kat.tehtavaryhma,
       kat.toimenpideinstanssi,
       kat.sopimus,
       tpik.nimi AS toimenpide
FROM kustannusarvioitu_tyo kat
       LEFT JOIN toimenpideinstanssi tpi ON kat.toimenpideinstanssi = tpi.id
       LEFT JOIN toimenpidekoodi tpik ON tpik.id = tpi.toimenpide
WHERE tpi.urakka = :urakka
ORDER BY vuosi, kuukausi;

-- name: hae-urakan-kustannusarvioidut-tyot-nimineen
-- Lähes sama kuin hae-kustannusarvoidut-tyot mutta palauttaa tyypin, tehtavan, ja toimenpiteen nimet
SELECT kat.id,
       kat.vuosi,
       kat.kuukausi,
       kat.summa,
       kat.tyyppi ::TOTEUMATYYPPI,
       tpik_t.nimi AS "tehtava-nimi",
       tr.nimi AS "tehtavaryhman-nimi",
       tpik_tpi.koodi AS "toimenpiteen-koodi",
       kat.sopimus
FROM kustannusarvioitu_tyo kat
       LEFT JOIN toimenpideinstanssi tpi ON kat.toimenpideinstanssi = tpi.id
       LEFT JOIN toimenpidekoodi tpik_tpi ON tpik_tpi.id = tpi.toimenpide
       LEFT JOIN toimenpidekoodi tpik_t ON tpik_t.id = kat.tehtava
       LEFT JOIN tehtavaryhma tr ON kat.tehtavaryhma = tr.id
WHERE tpi.urakka = :urakka;

-- name: paivita-kustannusarvioitu-tyo!
-- Päivittää kustannusarvoidun tyon summan, tunnisteena tpi, tehtävä-id, vuosi ja kk
UPDATE kustannusarvioitu_tyo
SET summa     = :summa,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE toimenpideinstanssi = :toimenpideinstanssi
  AND ((:tehtavaryhma::INTEGER IS NULL AND tehtavaryhma IS NULL) OR tehtavaryhma = :tehtavaryhma)
  AND ((:tehtava::INTEGER IS NULL AND tehtava IS NULL) OR tehtava = :tehtava)
  AND tyyppi = :tyyppi ::TOTEUMATYYPPI
  AND vuosi = :vuosi
  AND kuukausi = :kuukausi;

-- name: lisaa-kustannusarvioitu-tyo<!
-- Lisää kustannusarvioidunt tyon
INSERT INTO kustannusarvioitu_tyo
(vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja)
VALUES (:vuosi, :kuukausi, :summa, :tyyppi ::TOTEUMATYYPPI, :tehtava, :tehtavaryhma, :toimenpideinstanssi, :sopimus,
        current_timestamp, :kayttaja);

-- name: merkitse-kustannussuunnitelmat-likaisiksi!
-- Merkitsee teiden hoidon urakan (MHU) kustannussuunnitelmat likaiseksi urakkakohtaisen toimenpideinstanssin ja maksuerätyypin mukaan
UPDATE kustannussuunnitelma
SET likainen = TRUE,
    muokattu = current_timestamp
WHERE maksuera IN (SELECT m.numero
                   FROM maksuera m
                               JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
                   WHERE m.tyyppi IN (:maksueratyyppi ::MAKSUERATYYPPI)
                     AND tpi.id = :toimenpideinstanssi);
