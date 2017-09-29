DROP MATERIALIZED VIEW IF EXISTS sillat_alueurakoittain;
CREATE MATERIALIZED VIEW sillat_alueurakoittain AS
  SELECT
    u.id AS urakka,
    s.id AS silta
  FROM urakka u
    JOIN silta s
      ON ST_CONTAINS(u.alue, tierekisteriosoitteelle_piste(s.tr_numero, s.tr_alkuosa, s.tr_alkuetaisyys))
  WHERE u.tyyppi = 'hoito' :: URAKKATYYPPI;