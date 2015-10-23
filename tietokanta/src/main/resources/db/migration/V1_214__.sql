-- Kuvaus: pohjavesialueet halintayksiköittäin -näkymän päivittäminen
CREATE OR REPLACE FUNCTION paivita_hallintayksikoiden_pohjavesialueet()
  RETURNS VOID
SECURITY DEFINER
AS $$
BEGIN
  REFRESH MATERIALIZED VIEW pohjavesialueet_hallintayksikoittain;
RETURN;
END;
$$ LANGUAGE plpgsql;
;
