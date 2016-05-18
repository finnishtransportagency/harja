-- Tarkenna siltojen rajausta vain urakan alueelle

DROP MATERIALIZED VIEW sillat_alueurakoittain;

CREATE MATERIALIZED VIEW sillat_alueurakoittain AS
       SELECT u.id as urakka, s.id as silta
         FROM urakka u
	      JOIN hanke h ON u.hanke = h.id
	      JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro
	      JOIN silta s ON ST_CONTAINS(au.alue, s.alue)
        WHERE u.tyyppi = 'hoito'::urakkatyyppi;

