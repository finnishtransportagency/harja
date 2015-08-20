DROP MATERIALIZED VIEW sillat_alueurakoittain;

CREATE MATERIALIZED VIEW sillat_alueurakoittain AS
  SELECT
    s.id AS silta,
    u.id AS urakka
  FROM urakka u
    JOIN hanke h ON u.hanke = h.id
    JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro
    JOIN silta s ON ST_CONTAINS(ST_ENVELOPE(au.alue), s.alue)
  WHERE u.tyyppi = 'hoito' :: urakkatyyppi;