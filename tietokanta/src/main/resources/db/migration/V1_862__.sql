-- Jotta saadaan lisää nopeutta valtakunnallisiin raportteihin, joissa haetaan toteumia, toteumien_tehtäviä sekä
-- toteumien_materiaaleja, niin koostetaan niistä materialized view
DROP MATERIALIZED VIEW if exists raportti_toteuma_maarat;
CREATE MATERIALIZED VIEW raportti_toteuma_maarat AS
SELECT
        t.id as id,
        t.urakka as urakka_id,
        t.sopimus as sopimus_id,
        t.alkanut as alkanut,
        t.paattynyt as paattynyt,
        t.luotu as luotu,
        t.tyyppi as tyyppi,
        tm.materiaalikoodi as materiaalikoodi,
        tm.maara as materiaalimaara,
        tt.toimenpidekoodi as toimenpidekoodi,
        tt.maara as tehtavamaara,
        o.id as hallintayksikko_id
  FROM
       urakka u
       JOIN toteuma t on t.urakka = u.id AND t.poistettu = FALSE
       JOIN toteuma_tehtava tt on tt.toteuma = t.id AND tt.poistettu = FALSE
       LEFT JOIN toteuma_materiaali tm on tm.toteuma = t.id AND tm.poistettu = FALSE,
       organisaatio o
 WHERE o.id = u.hallintayksikko
WITH NO DATA;

-- Lisätään muutama indeksi
create index raportti_toteuma_maarat_ind on raportti_toteuma_maarat (alkanut);
create index raportti_toteuma_maarat_hall_alk on raportti_toteuma_maarat (hallintayksikko_id, alkanut);
create index raportti_toteuma_maarat_u_alk on raportti_toteuma_maarat (urakka_id, alkanut);

-- Joka aamu klo 7.15 päivitetään raporttien cachet ja otetaan tämä uusin toteumien materialized view
-- siihen samaan prosessiin mukaan.
CREATE OR REPLACE FUNCTION paivita_raportti_cachet()
    RETURNS VOID
    SECURITY DEFINER
AS $$
BEGIN
    REFRESH MATERIALIZED VIEW raportti_toteutuneet_materiaalit;
    REFRESH MATERIALIZED VIEW raportti_pohjavesialueiden_suolatoteumat;
    REFRESH MATERIALIZED VIEW raportti_toteuma_maarat;
    RETURN;
END;
$$ LANGUAGE plpgsql;

