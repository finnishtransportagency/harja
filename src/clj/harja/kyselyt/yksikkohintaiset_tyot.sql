-- name: listaa-urakan-yksikkohintaiset-tyot
-- Hakee kaikki yksikkohintaiset-tyot
SELECT
  yt.alkupvm,
  yt.loppupvm,
  yt.maara,
  yt.yksikko,
  yt.yksikkohinta,
  yt.tehtava,
  yt.urakka,
  yt.sopimus,
  tk.nimi AS tehtavan_nimi
FROM yksikkohintainen_tyo yt
  LEFT JOIN toimenpidekoodi tk ON yt.tehtava = tk.id
WHERE urakka = :urakka

-- name: paivita-urakan-yksikkohintainen-tyo!
-- Päivittää urakan hoitokauden yksikkohintaiset tyot
UPDATE yksikkohintainen_tyo
SET maara = :maara, yksikko = :yksikko, yksikkohinta = :yksikkohinta
WHERE urakka = :urakka AND sopimus = :sopimus AND tehtava = :tehtava
      AND alkupvm = :alkupvm AND loppupvm = :loppupvm

-- name: lisaa-urakan-yksikkohintainen-tyo<!
INSERT INTO yksikkohintainen_tyo
(maara, yksikko, yksikkohinta,
 urakka, sopimus, tehtava,
 alkupvm, loppupvm)
VALUES (:maara, :yksikko, :yksikkohinta,
        :urakka, :sopimus, :tehtava,
        :alkupvm, :loppupvm)

-- name: merkitse-kustannussuunnitelmat-likaisiksi!
-- Merkitsee yksikköhintaisia töitä vastaavat kustannussuunnitelmat likaisiksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE kustannussuunnitelma
SET likainen = TRUE
WHERE maksuera in (SELECT m.numero
                  FROM maksuera m
                    JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
                    join toimenpidekoodi emo on emo.id = tpi.toimenpide
                    join toimenpidekoodi tpk on tpk.emo = emo.id
                  WHERE m.tyyppi = 'yksikkohintainen' AND tpi.urakka = :urakka and tpk.id in (:tehtavat));

