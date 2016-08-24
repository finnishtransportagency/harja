-- Tee tarkastuksen ulkoinen_id,luoja parista uniikki

-- Kun tarkstus poistetaan, poistetaan viittaavat myös
ALTER TABLE talvihoitomittaus
      DROP CONSTRAINT talvihoitomittaus_tarkastus_fkey,
      ADD CONSTRAINT talvihoitomittaus_tarkastus_fkey
          FOREIGN KEY (tarkastus)
          REFERENCES tarkastus (id) ON DELETE CASCADE;

ALTER TABLE soratiemittaus
      DROP CONSTRAINT soratiemittaus_tarkastus_fkey,
      ADD CONSTRAINT soratiemittaus_tarkastus_fkey
          FOREIGN KEY (tarkastus)
	  REFERENCES tarkastus (id) ON DELETE CASCADE;

ALTER TABLE tarkastus_kommentti
      DROP CONSTRAINT tarkastus_kommentti_tarkastus_fkey,
      ADD CONSTRAINT tarkastus_kommentti_tarkastus_fkey
          FOREIGN KEY (tarkastus)
	  REFERENCES tarkastus (id) ON DELETE CASCADE;

ALTER TABLE tarkastus_laatupoikkeama
      DROP CONSTRAINT tarkastus_laatupoikkeama_tarkastus_fkey,
      ADD CONSTRAINT tarkastus_laatupoikkeama_tarkastus_fkey
          FOREIGN KEY (tarkastus)
	  REFERENCES tarkastus (id) ON DELETE CASCADE;

ALTER TABLE tarkastus_liite
      DROP CONSTRAINT tarkastus_liite_tarkastus_fkey,
      ADD CONSTRAINT tarkastus_liite_tarkastus_fkey
          FOREIGN KEY (tarkastus)
	  REFERENCES tarkastus (id) ON DELETE CASCADE;

ALTER TABLE tarkastus_vakiohavainto
      DROP CONSTRAINT tarkastus_vakiohavainto_tarkastus_fkey,
      ADD CONSTRAINT tarkastus_vakiohavainto_tarkastus_fkey
          FOREIGN KEY (tarkastus)
	  REFERENCES tarkastus (id) ON DELETE CASCADE;

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
