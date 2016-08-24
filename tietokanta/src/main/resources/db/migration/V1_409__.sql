-- Tee tarkastuksen ulkoinen_id,luoja parista uniikki

-- Poista duplikaatit tarkastukset ennen constraintin luontia
DELETE FROM
  tarkastus
 WHERE id IN (SELECT id
                FROM (SELECT id, ROW_NUMBER() OVER (partition BY ulkoinen_id, luoja ORDER BY id) AS rnum
		        FROM tarkastus) t
	       WHERE t.rnum > 1);

-- Luodaan unique constraint
ALTER TABLE tarkastus
  ADD CONSTRAINT tarkastus_ulkoinen_id_luoja_uniikki UNIQUE (ulkoinen_id, luoja);
