CREATE MATERIALIZED VIEW urakoiden_alueet AS
SELECT u.id, u.tyyppi,

  CASE
  WHEN u.tyyppi='hoito' THEN au.alue
  ELSE u.alue
  END

FROM urakka u
  LEFT JOIN hanke h ON u.hanke = h.id
  LEFT JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro;