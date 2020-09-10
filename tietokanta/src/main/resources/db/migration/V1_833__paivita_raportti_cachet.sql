-- Lisätään refresh komentoihin concurrently eli päivitys ei lukita haettavia tauluja

CREATE OR REPLACE FUNCTION paivita_raportti_cachet()
    RETURNS VOID
    SECURITY DEFINER
AS $$
BEGIN
    REFRESH MATERIALIZED VIEW concurrently raportti_toteutuneet_materiaalit;
    REFRESH MATERIALIZED VIEW concurrently raportti_pohjavesialueiden_suolatoteumat;
    RETURN;
END;
$$ LANGUAGE plpgsql;

-- Koska CI putkessa tyhjässä kannassa ei ole vieweissa mitään tarvitaan erillinen refresh.
-- Tätä ei koskaan käytetä tuotannossa.
CREATE OR REPLACE FUNCTION paivita_raportti_cachet_testeille()
    RETURNS VOID
    SECURITY DEFINER
AS $$
BEGIN
    REFRESH MATERIALIZED VIEW raportti_toteutuneet_materiaalit;
    REFRESH MATERIALIZED VIEW raportti_pohjavesialueiden_suolatoteumat;
    RETURN;
END;
$$ LANGUAGE plpgsql;