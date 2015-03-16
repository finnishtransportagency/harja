-- name: listaa-urakan-yksikkohintaiset-tyot
-- Hakee kaikki yksikkohintaiset-tyot
SELECT yt.alkupvm, yt.loppupvm, yt.maara, yt.yksikko, yt.yksikkohinta, yt.tehtava, yt.urakka, yt.sopimus,
	   tk.nimi as tehtavan_nimi
  FROM yksikkohintainen_tyo yt
	   LEFT JOIN toimenpidekoodi tk ON yt.tehtava = tk.id
 WHERE urakka = :urakka

 -- name: paivita-urakan-yksikkohintainen-tyo!
-- Päivittää urakan hoitokauden yksikkohintaiset tyot

-- {:yhteensa 1077827.3005, :loppupvm #inst "2005-09-29T21:00:00.000-00:00", :yksikko km, :tehtava 1350, :urakka 1, 
-- :yksikkohinta 525.5, :maara 1525.321, :tehtavan_nimi Tien auraaminen, :sopimus 2, :alkupvm #inst "2005-12-31T22:00:00.000-00:00"}
UPDATE yksikkohintainen_tyo
   SET maara =:maara, yksikko =:yksikko, yksikkohinta =:yksikkohinta
 WHERE urakka = :urakka AND sopimus = :sopimus AND tehtava = :tehtava 
 	   AND alkupvm =:alkupvm AND loppupvm =:loppupvm 

 	   -- AND extract (year from alkupvm) = :alkupvm_vuosi 
 	   -- AND extract (month from alkupvm) = :alkupvm_kk
 	   -- AND extract (day from alkupvm) = :alkupvm_pv
 	   -- AND extract (year from loppupvm) = :loppupvm_vuosi 
 	   -- AND extract (month from loppupvm) = :loppupvm_kk
 	   -- AND extract (day from loppupvm) = :loppupvm_pv
 	   --SET alkupvm =:alkupvm, loppupvm =:loppupvm, maara =:maara, yksikko =:yksikko,
   --yksikkohinta =:yksikkohinta, tehtava =:tehtava, urakka =:urakka, sopimus =:sopimus,

-- name: lisaa-urakan-yksikkohintainen-tyo
INSERT INTO yksikkohintainen_tyo 
            (maara, yksikko, yksikkohinta, 
             urakka, sopimus, tehtava,
             alkupvm, loppupvm)
	 VALUES (:maara, :yksikko, :yksikkohinta, 
	 		 :urakka, :sopimus, :tehtava, 
	 		 :alkupvm, :loppupvm)	 		