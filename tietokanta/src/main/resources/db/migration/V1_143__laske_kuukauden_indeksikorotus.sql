-- esim. indeksin arvo 105, summa 1000e:
-- summa indeksillä korotettuna 1 000 € * (105/100) = 1050,00 €


CREATE OR REPLACE FUNCTION kuukauden_indeksikorotus(pvm date, indeksinimi varchar, summa NUMERIC) RETURNS NUMERIC(10,2) AS $$
DECLARE
  kerroin numeric;
BEGIN
  -- Kerroin on ko. indeksin arvo ko. kuukautena ja vuonna
  SELECT
    INTO kerroin
    arvo/100.0
  FROM indeksi
  WHERE nimi = indeksinimi
        AND vuosi = (SELECT EXTRACT(YEAR FROM pvm)) AND kuukausi = (SELECT EXTRACT(MONTH FROM pvm));
  -- Jos yhtään indeksilukuja ei ole, kerroin on NULL, jolloin myös
  -- tämä lasku palauttaa NULL.
  RETURN summa * kerroin;
END;
$$ LANGUAGE plpgsql;
