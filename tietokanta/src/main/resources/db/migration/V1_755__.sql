CREATE MATERIALIZED VIEW raportti_toteutuneet_materiaalit AS
  SELECT
    SUM(tm.maara)                AS kokonaismaara,
    u.id                         AS "urakka-id",
    mk.id                        AS "materiaali-id",
    date_trunc('day', t.alkanut) AS paiva
  FROM toteuma t
    JOIN toteuma_materiaali tm ON t.id = tm.toteuma
                                  AND tm.poistettu IS NOT TRUE
    LEFT JOIN materiaalikoodi mk ON mk.id = tm.materiaalikoodi
    JOIN urakka u ON u.id = t.urakka
  WHERE t.poistettu IS NOT TRUE
  GROUP BY "urakka-id", paiva, "materiaali-id";

CREATE OR REPLACE FUNCTION paivita_raportti_cachet()
  RETURNS VOID
SECURITY DEFINER
AS $$
BEGIN
  REFRESH MATERIALIZED VIEW raportti_toteutuneet_materiaalit;
  RETURN;
END;
$$ LANGUAGE plpgsql;