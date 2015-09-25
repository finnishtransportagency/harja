-- name: listaa-urakan-yksikkohintaiset-tyot
-- Hakee kaikki yksikkohintaiset-tyot
SELECT
  yt.id,
  yt.alkupvm,
  yt.loppupvm,
  yt.maara,
  yt.yksikko,
  yt.yksikkohinta,
  yt.tehtava,
  yt.urakka,
  yt.sopimus,
  tk.id   AS tehtavan_id,
  tk.nimi AS tehtavan_nimi
FROM yksikkohintainen_tyo yt
  LEFT JOIN toimenpidekoodi tk ON yt.tehtava = tk.id
WHERE urakka = :urakka;

-- name: hae-urakan-sopimuksen-yksikkohintaiset-tehtavat
-- Urakan sopimuksen yksikköhintaiset tehtävät
SELECT
  id,
  nimi
FROM toimenpidekoodi
WHERE id IN (
  SELECT DISTINCT (tehtava)
  FROM yksikkohintainen_tyo
  WHERE urakka = :urakkaid AND sopimus = :sopimusid) AND
      (kokonaishintainen IS NULL OR NOT kokonaishintainen);

-- name: paivita-urakan-yksikkohintainen-tyo!
-- Päivittää urakan hoitokauden yksikkohintaiset tyot
UPDATE yksikkohintainen_tyo
SET maara = :maara, yksikko = :yksikko, yksikkohinta = :yksikkohinta
WHERE urakka = :urakka AND sopimus = :sopimus AND tehtava = :tehtava
      AND alkupvm = :alkupvm AND loppupvm = :loppupvm;

-- name: lisaa-urakan-yksikkohintainen-tyo<!
INSERT INTO yksikkohintainen_tyo
(maara, yksikko, yksikkohinta,
 urakka, sopimus, tehtava,
 alkupvm, loppupvm)
VALUES (:maara, :yksikko, :yksikkohinta,
        :urakka, :sopimus, :tehtava,
        :alkupvm, :loppupvm);

-- name: merkitse-kustannussuunnitelmat-likaisiksi!
-- Merkitsee yksikköhintaisia töitä vastaavat kustannussuunnitelmat likaisiksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE kustannussuunnitelma
SET likainen = TRUE
WHERE maksuera IN (SELECT m.numero
                   FROM maksuera m
                     JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
                     JOIN toimenpidekoodi emo ON emo.id = tpi.toimenpide
                     JOIN toimenpidekoodi tpk ON tpk.emo = emo.id
                   WHERE m.tyyppi = 'yksikkohintainen' AND tpi.urakka = :urakka AND tpk.id IN (:tehtavat));

