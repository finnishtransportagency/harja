DROP MATERIALIZED VIEW IF EXISTS raportti_toteutuneet_materiaalit;
CREATE MATERIALIZED VIEW raportti_toteutuneet_materiaalit AS
SELECT SUM(tm.maara)                AS kokonaismaara,
       t.urakka                     AS "urakka-id",
       mk.id                        AS "materiaali-id",
       date_trunc('day', t.alkanut) AS paiva
    FROM toteuma_materiaali tm
             JOIN toteuma t ON t.id = tm.toteuma AND t.poistettu IS NOT TRUE
             LEFT JOIN materiaalikoodi mk ON mk.id = tm.materiaalikoodi
    WHERE tm.poistettu = FALSE
    GROUP BY "urakka-id", paiva, "materiaali-id";

CREATE OR REPLACE FUNCTION paivita_raportti_toteutuneet_materiaalit()
    RETURNS VOID
    SECURITY DEFINER
AS $$
BEGIN
    REFRESH MATERIALIZED VIEW raportti_toteutuneet_materiaalit;
    RETURN;
END;
$$ LANGUAGE plpgsql;
