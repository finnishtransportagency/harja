-- name: hae-kustannusarvioidut-tyot
-- Hakee kaikki urakan kustannusarvioidut tyot
SELECT
  kat.id,
  kat.vuosi,
  kat.kuukausi,
  kat.summa,
  kat.tyyppi,
  kat.tehtava,
  kat.tehtavaryhma,
  kat.toimenpideinstanssi,
  kat.sopimus,
  kat."osuus-hoitokauden-summasta",
  tpi.id         AS tpi_id,
  tpi.nimi       AS tpi_nimi,
  tpi.toimenpide AS toimenpide
FROM kustannusarvioitu_tyo kat
  LEFT JOIN toimenpideinstanssi tpi ON kat.toimenpideinstanssi = tpi.id
WHERE tpi.urakka = :urakka
ORDER BY vuosi, kuukausi;

-- name: paivita-kustannusarvioitu-tyo!
-- Päivittää kustannusarvoidun tyon summan, tunnisteena tpi, tehtävä-id, vuosi ja kk
UPDATE kustannusarvioitu_tyo
SET
summa = :summa,
muokattu = current_timestamp,
muokkaaja = :kayttaja
WHERE toimenpideinstanssi = :toimenpideinstanssi AND tyyppi = :tyyppi
      AND vuosi = :vuosi AND kuukausi = :kuukausi;


-- name: lisaa-kustannusarvioitu-tyo<!
-- Lisää kustannusarvioidunt tyon
INSERT INTO kustannusarvioitu_tyo
(vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja)
VALUES (:summa, :osuus-hoitokauden-summasta, :tehtava, :toimenpideinstanssi, :sopimus, :vuosi, :kuukausi, :kayttaja);

