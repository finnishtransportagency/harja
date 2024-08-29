-- Kojelautaa, eli (hoito)urakoiden tietojen yleisnäkymää varten tarvittavia apufunktioita

CREATE OR REPLACE FUNCTION urakan_kustannussuunnitelman_tila(urakka INTEGER, hoitovuoden_alkuvuosi INTEGER)
    RETURNS BOOLEAN AS
$$
BEGIN
    RETURN (urakka % 5 = 0);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION urakan_tehtavamaarien_suunnittelun_tila(urakka INTEGER, hoitovuoden_alkuvuosi INTEGER)
    RETURNS BOOLEAN AS
$$
BEGIN
    RETURN (urakka % 3 = 0);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION urakan_rajoitusalueiden_suunnittelun_tila(urakka INTEGER, hoitovuoden_alkuvuosi INTEGER)
    RETURNS BOOLEAN AS
$$
BEGIN
    RETURN (urakka % 2 = 0);
END;
$$ LANGUAGE plpgsql;

