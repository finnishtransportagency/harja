-- Kuvaus: Tee stored procedure urakoiden alueiden materialisoidun näkymän päivittämiseksi
-- HUOM! Jatkossa kaikille materialisoiduille näkymille tarvitaan stored procedure ja sille oikeudet afterMigrate.sql fileen
CREATE OR REPLACE FUNCTION paivita_urakoiden_alueet()
  RETURNS VOID
SECURITY DEFINER
AS $$
BEGIN
  REFRESH MATERIALIZED VIEW urakoiden_alueet;
RETURN;
END;
$$ LANGUAGE plpgsql;
