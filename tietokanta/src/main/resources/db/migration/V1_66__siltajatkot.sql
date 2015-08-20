-- unohdettu siltaviittaus tarkastuksesta

ALTER TABLE siltatarkastus
 ADD COLUMN silta integer NOT NULL REFERENCES silta (id);

ALTER TABLE siltatarkastus
 ADD COLUMN urakka integer NOT NULL REFERENCES urakka (id);

CREATE MATERIALIZED VIEW sillat_alueurakoittain AS
       SELECT s.id as silta,
              (SELECT u.id
                 FROM urakka u
                      JOIN hanke h ON u.hanke = h.id
	              JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro
                WHERE u.tyyppi='hoito'::urakkatyyppi
 	          AND ST_CONTAINS(au.alue, s.alue)) as urakka
         FROM silta s;
	 
                         
