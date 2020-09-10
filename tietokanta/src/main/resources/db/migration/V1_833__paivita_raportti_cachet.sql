-- Lisätään refresh komentoihin concurrently eli päivitys ei lukita haettavia tauluja
-- Koska concurrently vaatii uniikit indeksit, ne luodaan

CREATE OR REPLACE FUNCTION paivita_raportti_cachet()
    RETURNS VOID
    SECURITY DEFINER
AS $$
BEGIN
    CREATE UNIQUE INDEX ON raportti_toteutuneet_materiaalit ("urakka-id", "materiaali-id", paiva);
    CREATE UNIQUE INDEX ON raportti_pohjavesialueiden_suolatoteumat
        ("urakka-id", tie,alkuosa, alkuet, paiva, pituus, tunnus, kayttoraja);
    REFRESH MATERIALIZED VIEW concurrently raportti_toteutuneet_materiaalit;
    REFRESH MATERIALIZED VIEW concurrently raportti_pohjavesialueiden_suolatoteumat;
    RETURN;
END;
$$ LANGUAGE plpgsql;