-- name: hae-urakan-toimenpiteet-ja-tehtavat
-- Hakee kaikki urakan 3. ja 4. tason toimenpiteet 
SELECT t4.id as t4_id, t4.koodi as t4_koodi, t4.nimi as t4_nimi, t4.yksikko as t4_yksikko,
       t3.id as t3_id, t3.koodi as t3_koodi, t3.nimi as t3_nimi,
       t2.id as t2_id, t2.koodi as t2_koodi, t2.nimi as t2_nimi,
       t1.id as t1_id, t1.koodi as t1_koodi, t1.nimi as t1_nimi
  FROM toimenpidekoodi t4
       LEFT JOIN toimenpidekoodi t3 ON t3.id=t4.emo
       LEFT JOIN toimenpidekoodi t2 ON t2.id=t3.emo
       LEFT JOIN toimenpidekoodi t1 ON t1.id=t2.emo
 WHERE t4.taso = 4 AND
       t3.id in (SELECT toimenpide FROM toimenpideinstanssi WHERE urakka = :urakka) AND
       t4.poistettu = false


  -- name: hae-urakan-toimenpiteet
-- Hakee kaikki urakan 3. tason toimenpiteet
SELECT 	tpi.toimenpide as id,
		    t3.nimi as t3_nimi, t3.koodi as t3_koodi,
		    t3.emo as t3_emo,
	    	t2.nimi as t2_nimi, t2.koodi as t2_koodi,
		    t2.emo as t2_emo,
	     	t1.nimi as t1_nimi, t1.koodi as t1_koodi
  FROM 	toimenpideinstanssi tpi
        LEFT JOIN  toimenpidekoodi t3 ON tpi.toimenpide = t3.id
        LEFT JOIN  toimenpidekoodi t2 ON t2.id=t3.emo
        LEFT JOIN  toimenpidekoodi t1 ON t1.id=t2.emo
  WHERE	urakka = :urakka
