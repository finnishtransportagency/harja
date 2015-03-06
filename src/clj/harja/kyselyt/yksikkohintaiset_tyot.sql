-- name: listaa-urakan-yksikkohintaiset-tyot
-- Hakee kaikki yksikkohintaiset-tyot
SELECT 	  alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka
  FROM 	  yksikkohintainen_tyo
  	WHERE urakka = :urakka