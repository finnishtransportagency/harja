ALTER TABLE urakka_tavoite ADD COLUMN tarjous_tavoitehinta NUMERIC;
COMMENT ON COLUMN urakka_tavoite.tarjous_tavoitehinta IS 'Tarjouksen mukainen alkuperäinen tavoitehinta.';
