-- Nimi: Urakan suolasakkojen summan laskenta
-- Kuvaus: Lisää urakan suolasakon summan laskennan stored proceduren. Parametrinä annetaan urakan id.
-- Palauttaa suolasakkojen summan.

CREATE OR REPLACE FUNCTION urakan_suolasakot(urakka_id INTEGER)
  RETURNS NUMERIC LANGUAGE plpgsql AS $$
DECLARE
  suolasakkojen_summa NUMERIC;
  hoitokausi          paivamaaravali%ROWTYPE;
  suolasakon_summa    NUMERIC;
BEGIN
  suolasakkojen_summa := 0;

  FOR hoitokausi IN SELECT *
                    FROM urakan_hoitokaudet(urakka_id)
  LOOP
    suolasakon_summa := hoitokauden_suolasakko(urakka_id, hoitokausi.alkupvm, hoitokausi.loppupvm);

    IF suolasakon_summa IS NOT NULL
    THEN
      suolasakkojen_summa := suolasakkojen_summa + suolasakon_summa;
    END IF;

  END LOOP;

  RETURN suolasakkojen_summa;
END
$$;