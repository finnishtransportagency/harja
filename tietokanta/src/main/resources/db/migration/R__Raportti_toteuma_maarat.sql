-- Jotta saadaan lisää nopeutta valtakunnallisiin raportteihin, joissa haetaan toteumia, toteumien_tehtäviä sekä
-- toteumien_materiaaleja, niin koostetaan niistä materialized view
DROP MATERIALIZED VIEW IF EXISTS raportti_toteuma_maarat;
CREATE MATERIALIZED VIEW raportti_toteuma_maarat AS
SELECT
    MAX(t.id)               as id,
    t.urakka           as urakka_id,
    t.sopimus          as sopimus_id,
    t.alkanut::DATE    as alkanut,
    MAX(t.paattynyt)   as paattynyt,
    t.tyyppi           as tyyppi,
    tm.materiaalikoodi as materiaalikoodi,
    SUM(tm.maara)           as materiaalimaara,
    tt.toimenpidekoodi as toimenpidekoodi,
    SUM(tt.maara)           as tehtavamaara,
    o.id               as hallintayksikko_id
FROM
    urakka u
        JOIN toteuma t on t.urakka = u.id AND t.poistettu = FALSE
        JOIN toteuma_tehtava tt on tt.toteuma = t.id AND tt.poistettu = FALSE
        LEFT JOIN toteuma_materiaali tm on tm.toteuma = t.id AND tm.poistettu = FALSE,
    organisaatio o
WHERE o.id = u.hallintayksikko
GROUP BY t.alkanut::DATE, t.urakka, t.sopimus, t.tyyppi, tm.materiaalikoodi, tt.toimenpidekoodi, o.id;

-- Lisätään muutama indeksi
CREATE INDEX IF NOT EXISTS raportti_toteuma_maarat_ind on raportti_toteuma_maarat (alkanut);
CREATE INDEX IF NOT EXISTS raportti_toteuma_maarat_hall_alk on raportti_toteuma_maarat (hallintayksikko_id, alkanut);
CREATE INDEX IF NOT EXISTS raportti_toteuma_maarat_u_alk on raportti_toteuma_maarat (urakka_id, alkanut);


CREATE OR REPLACE FUNCTION paivita_raportti_toteuma_maarat()
    RETURNS VOID
    SECURITY DEFINER
AS $$
BEGIN
    REFRESH MATERIALIZED VIEW raportti_toteuma_maarat;
    RETURN;
END;
$$ LANGUAGE plpgsql;
