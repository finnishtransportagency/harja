-- name: hae-urakan-toimenpiteet-ja-tehtavat
-- Hakee kaikki urakan 3. ja 4. tason toimenpiteet 
SELECT 	id,koodi,nimi,emo,taso
 FROM 	toimenpidekoodi
  WHERE id in (SELECT toimenpide FROM urakka_toimenpide WHERE urakka = :urakka) OR
    	emo in (SELECT toimenpide FROM urakka_toimenpide WHERE urakka = :urakka)


  -- name: hae-urakan-toimenpiteet
-- Hakee kaikki urakan 3. tason toimenpiteet
SELECT 	ut.toimenpide as id, 
		tk.nimi as nimi, tk.koodi as koodi,
		tk.emo as emo, tk.taso as taso
  FROM 	urakka_toimenpide ut
  LEFT JOIN  toimenpidekoodi tk ON ut.toimenpide = tk.id
  WHERE	urakka = :urakka