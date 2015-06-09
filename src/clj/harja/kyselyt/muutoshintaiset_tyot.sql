-- name: listaa-urakan-muutoshintaiset-tyot
-- Hakee kaikki muutoshintaiset-tyot
SELECT
       mt.id,
       mt.alkupvm,
       mt.loppupvm,
       mt.yksikko,
       mt.yksikkohinta,
       mt.tehtava,
       mt.urakka,
       mt.sopimus,
       tk.nimi AS tehtavanimi,
       tpi.id AS toimenpideinstanssi
  FROM muutoshintainen_tyo mt
       JOIN toimenpidekoodi tk ON mt.tehtava = tk.id
       JOIN toimenpideinstanssi tpi ON tk.emo = tpi.toimenpide
 WHERE mt.urakka = :urakka AND tpi.urakka = mt.urakka;

-- name: paivita-urakan-muutoshintainen-tyo!
-- Päivittää urakan hoitokauden muutoshintaiset tyot
UPDATE muutoshintainen_tyo
   SET yksikko = :yksikko, yksikkohinta = :yksikkohinta
 WHERE urakka = :urakka AND sopimus = :sopimus AND tehtava = :tehtava
      AND alkupvm = :alkupvm AND loppupvm = :loppupvm;

-- name: lisaa-urakan-muutoshintainen-tyo<!
INSERT INTO yksikkohintainen_tyo
(yksikko, yksikkohinta,
 urakka, sopimus, tehtava,
 alkupvm, loppupvm)
VALUES (:yksikko, :yksikkohinta,
        :urakka, :sopimus, :tehtava,
        :alkupvm, :loppupvm);
