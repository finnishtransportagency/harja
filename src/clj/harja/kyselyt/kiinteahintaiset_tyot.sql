-- name: hae-kiinteahintaiset-tyot
-- Hakee kaikki urakan kiinteahintaiset tyot
SELECT
  kht.id,
  kht.vuosi,
  kht.kuukausi,
  kht.summa,
  kht.tyyppi,
  kht.tehtava,
  kht.tehtavaryhma,
  kht.toimenpideinstanssi,
  kht.sopimus,
  kht."osuus-hoitokauden-summasta",
  tpi.id         AS tpi_id,
  tpi.nimi       AS tpi_nimi,
  tpi.toimenpide AS toimenpide
FROM kiinteahintainen_tyo kht
  LEFT JOIN toimenpideinstanssi tpi ON kht.toimenpideinstanssi = tpi.id
WHERE tpi.urakka = :urakka
ORDER BY vuosi, kuukausi;

-- name: paivita-kiinteahintainen-tyo!
-- Päivittää kiinteahintaisen tyon summan, tunnisteena tpi, tehtävä-id, vuosi ja kk
UPDATE kiinteahintainen_tyo
SET
summa = :summa,
muokattu = current_timestamp,
muokkaaja = :kayttaja
WHERE toimenpideinstanssi = :toimenpideinstanssi
      AND vuosi = :vuosi AND kuukausi = :kuukausi;


-- name: lisaa-kustannusarvioitu-tyo<!
-- Lisää kustannusarvioidun tyon
INSERT INTO kiinteahintainen_tyo
(vuosi, kuukausi, summa, toimenpideinstanssi, sopimus, luotu, luoja)
VALUES (:vuosi, :kuukausi, :summa, :toimenpideinstanssi, :sopimus, current_timestamp , :kayttaja);

