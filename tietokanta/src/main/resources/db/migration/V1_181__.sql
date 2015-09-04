-- esim. indeksi pisteluvut: loka, marras, joulu, tammi, helmi, maalis
--                             98,    102,   110,   115,   104,    101
--                keskiarvo: 105
-- esim sakkosumma: 1 234 € * (105/100) = 1295,70 €
CREATE TYPE indeksitarkistettu_suolasakko_rivi AS (
  summa       NUMERIC,
  korotettuna NUMERIC,
  korotus     NUMERIC);

CREATE OR REPLACE FUNCTION laske_suolasakon_indeksitarkistus(talvikauden_alkuvuosi integer, indeksinimi varchar, summa NUMERIC)
  RETURNS indeksitarkistettu_suolasakko_rivi AS $$
DECLARE
  kerroin numeric;
  alkuv integer;
  loppuv integer;
BEGIN
  alkuv := talvikauden_alkuvuosi;
  loppuv := talvikauden_alkuvuosi + 1;

  -- Kerroin on talvikauden alkuvuoden loka,marras,joulu kuukausien sekä
  -- seuraavan vuoden tammi,helmi,maalis kuukausien prosenttiarvojen
  -- keskiarvo kertoimena.
  SELECT
    INTO kerroin
         AVG(arvo)/100.0
    FROM indeksi
   WHERE nimi = indeksinimi
     AND ((vuosi = alkuv  AND kuukausi = 10) OR
          (vuosi = alkuv  AND kuukausi = 11) OR
          (vuosi = alkuv  AND kuukausi = 12) OR
          (vuosi = loppuv AND kuukausi = 1) OR
          (vuosi = loppuv AND kuukausi = 2) OR
          (vuosi = loppuv AND kuukausi = 3));
  -- Jos yhtään indeksilukuja ei ole, kerroin on NULL, jolloin myös
  -- tämä lasku palauttaa NULL.
  RETURN (summa, summa * kerroin, summa * kerroin - summa);
END;
$$ LANGUAGE plpgsql;
