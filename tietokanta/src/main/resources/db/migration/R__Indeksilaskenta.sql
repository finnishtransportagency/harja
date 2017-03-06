-- Indeksilaskennan perusluvut

CREATE OR REPLACE FUNCTION hoitourakan_indeksilaskennan_perusluku(urakka_id INTEGER)
  RETURNS NUMERIC AS $$
DECLARE
  kilpailutusta_edeltava_vuosi INTEGER;
  kilpailutusvuosi             INTEGER;
  tulosrivi                    RECORD;
  indeksinimi TEXT;
BEGIN
  SELECT indeksi FROM urakka WHERE id = urakka_id INTO indeksinimi;
  IF indeksinimi IS NULL THEN
    RAISE NOTICE 'Indeksit eivät ole käytössä urakassa %', urakka_id;
    RETURN NULL;
  ELSE
    SELECT INTO kilpailutusvuosi (SELECT EXTRACT(YEAR FROM (SELECT alkupvm
                                                            FROM urakka
                                                            WHERE id = urakka_id)));
    kilpailutusta_edeltava_vuosi := kilpailutusvuosi - 1;
    -- Perusluku on urakan kilpailuttamisvuotta ed. joulukuun ja kilp.vuoden tammi- ja helmikuun pistelukujen keskiarvo
    SELECT INTO tulosrivi
      AVG(arvo) AS perusluku,
      count(*)  AS indeksilukujen_lkm
    FROM indeksi
    WHERE nimi = indeksinimi
          AND ((vuosi = kilpailutusta_edeltava_vuosi AND kuukausi = 12) OR
               (vuosi = kilpailutusvuosi AND kuukausi = 1) OR
               (vuosi = kilpailutusvuosi AND kuukausi = 2)
          );
    RAISE NOTICE 'Laskettiin hoitourakan id:llä % indeksilaskennan perusluvuksi:  %, käytetty indeksi: %', urakka_id, tulosrivi.perusluku, indeksinimi;
    -- Jos kaikkia kolmea indeksilukua ei ole, palautetaan NULL
    IF (tulosrivi.indeksilukujen_lkm = 3)
    THEN
      RETURN round(tulosrivi.perusluku, 1);
    ELSE
      RAISE NOTICE 'Peruslukua ei voitu laskea koska indeksilukuja indeksillä % joulu-helmikuun aikana %-% löytyi vain : %', indeksinimi, kilpailutusta_edeltava_vuosi, kilpailutusvuosi, tulosrivi.indeksilukujen_lkm;
      RETURN NULL;
    END IF;
  END IF;
END;
$$ LANGUAGE plpgsql;

-- Laske kuukauden indeksikorotus
-- indeksikorotus lasketaan yleensä (aina?) (vertailuluku/perusluku) * summa
-- esim. indeksin arvo 105.0, perusluku 103.2, summa 1000e:
-- summa indeksillä korotettuna 1 000 € * (105/103.2) = 1 017,44 €
CREATE OR REPLACE FUNCTION laske_kuukauden_indeksikorotus(
  v          INTEGER,
  kk          INTEGER,
  indeksinimi VARCHAR,
  summa      NUMERIC,
  perusluku  NUMERIC)

  RETURNS kuukauden_indeksikorotus_rivi AS $$
DECLARE
  kerroin      NUMERIC;
  vertailuluku NUMERIC;

BEGIN
  -- Jos maksu on päätetty olla sitomatta indeksiin (tai urakassa ei ole indeksit käytössä),
  -- palautetaan (summa, summa, 0)
  IF indeksinimi IS NULL
  THEN
    RAISE NOTICE 'Indeksiä ei käytetty tässä maksussa.';
    RETURN (summa, summa, 0 :: NUMERIC);
  END IF;

  -- Perusluku puuttuu
  IF perusluku IS NULL THEN
    RAISE NOTICE 'Kuukauden indeksikorotusta ei voitu laskea koska peruslukua ei ole';
    RETURN (summa, NULL :: NUMERIC, NULL :: NUMERIC);
  END IF;


  SELECT
    INTO vertailuluku arvo
  FROM indeksi
  WHERE nimi = indeksinimi
        AND vuosi = v AND kuukausi = kk;
  -- Jos yhtään indeksilukuja ei ole, kerroin on NULL, jolloin myös
  -- tämä lasku palauttaa NULL.
  kerroin := (vertailuluku / perusluku);
  RETURN (summa, summa * kerroin, summa * kerroin - summa);
END;
$$ LANGUAGE plpgsql;
