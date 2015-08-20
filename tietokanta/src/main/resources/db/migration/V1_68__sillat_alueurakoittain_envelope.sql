DROP MATERIALIZED VIEW sillat_alueurakoittain;

CREATE MATERIALIZED VIEW sillat_alueurakoittain AS
       SELECT s.id as silta,
              (SELECT u.id
                 FROM urakka u
                      JOIN hanke h ON u.hanke = h.id
	              JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro
                WHERE u.tyyppi='hoito'::urakkatyyppi
 	          AND ST_CONTAINS(ST_ENVELOPE(au.alue), s.alue)) as urakka
         FROM silta s;