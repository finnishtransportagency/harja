-- name: listaa-urakan-yksikkohintaiset-tyot
-- Hakee kaikki yksikkohintaiset-tyot
SELECT yt.alkupvm, yt.loppupvm, yt.maara, yt.yksikko, yt.yksikkohinta, yt.tehtava, yt.urakka, yt.sopimus,
	   tk.nimi as tehtavan_nimi
  FROM yksikkohintainen_tyo yt
	   LEFT JOIN toimenpidekoodi tk ON yt.tehtava = tk.id
 WHERE urakka = :urakka