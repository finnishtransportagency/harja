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
       t3.id in (SELECT toimenpide FROM urakka_toimenpide WHERE urakka = :urakka)


  -- name: hae-urakan-toimenpiteet
-- Hakee kaikki urakan 3. tason toimenpiteet
SELECT 	ut.toimenpide as id, 
		tk.nimi as nimi, tk.koodi as koodi,
		tk.emo as emo, tk.taso as taso
  FROM 	urakka_toimenpide ut
  LEFT JOIN  toimenpidekoodi tk ON ut.toimenpide = tk.id
  WHERE	urakka = :urakka
