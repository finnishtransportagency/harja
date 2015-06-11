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
 WHERE mt.urakka = :urakka AND tpi.urakka = mt.urakka
       AND mt.poistettu != true;

-- name: paivita-muutoshintainen-tyo!
-- Päivittää urakan hoitokauden muutoshintaiset tyot
UPDATE muutoshintainen_tyo
   SET yksikko = :yksikko, yksikkohinta = :yksikkohinta, muokattu = NOW(), muokkaaja = :kayttaja
 WHERE urakka = :urakka AND sopimus = :sopimus AND tehtava = :tehtava
      AND alkupvm = :alkupvm AND loppupvm = :loppupvm;

-- name: poista-muutoshintainen-tyo!
-- Päivittää urakan hoitokauden muutoshintaiset tyot
UPDATE muutoshintainen_tyo
SET poistettu = true, yksikko = :yksikko, yksikkohinta = :yksikkohinta, muokattu = NOW(), muokkaaja = :kayttaja
WHERE urakka = :urakka AND sopimus = :sopimus AND tehtava = :tehtava
      AND alkupvm = :alkupvm AND loppupvm = :loppupvm;


-- name: lisaa-muutoshintainen-tyo<!
INSERT INTO muutoshintainen_tyo
(yksikko, yksikkohinta, muokattu, muokkaaja,
 urakka, sopimus, tehtava,
 alkupvm, loppupvm)
VALUES (:yksikko, :yksikkohinta, NOW(), :kayttaja,
        :urakka, :sopimus, :tehtava,
        :alkupvm, :loppupvm);
