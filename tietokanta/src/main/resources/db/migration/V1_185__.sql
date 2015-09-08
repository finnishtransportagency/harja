<<<<<<< HEAD
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('tierekisteri', 'hae-tietolaji');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('tierekisteri', 'hae-tietueet');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('tierekisteri', 'hae-tietue');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('tierekisteri', 'lisaa-tietue');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('tierekisteri', 'paivita-tietue');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('tierekisteri', 'poista-tietue');
=======
-- esim. indeksin arvo 105, summa 1000e:
-- summa indeksillä korotettuna 1 000 € * (105/100) = 1050,00 €
CREATE TYPE kuukauden_indeksikorotus_rivi AS (
  summa       NUMERIC,
  korotettuna NUMERIC,
  korotus     NUMERIC);

CREATE OR REPLACE FUNCTION laske_kuukauden_indeksikorotus(
  v           INTEGER,
  kk          INTEGER,
  indeksinimi VARCHAR,
  summa       NUMERIC)

  RETURNS kuukauden_indeksikorotus_rivi AS $$
DECLARE
  kerroin NUMERIC;
BEGIN
  -- Kerroin on ko. indeksin arvo ko. kuukautena ja vuonna
  SELECT
    INTO kerroin arvo / 100.0
  FROM indeksi
  WHERE nimi = indeksinimi
        AND vuosi = v AND kuukausi = kk;
  -- Jos yhtään indeksilukuja ei ole, kerroin on NULL, jolloin myös
  -- tämä lasku palauttaa NULL.
  RETURN (summa, summa * kerroin, summa * kerroin - summa);
END;
$$ LANGUAGE plpgsql;

>>>>>>> develop
